import type { LocalSession } from "@/api/auth"
import { deterministicId } from "@/vault/crypto"
import { liveAssets, liveValuations, assetTotals } from "./assets"
import { liveCashAccounts } from "./cashAccounts"
import { liveDebts, livePayments } from "./debts"
import { liveDisposalLots, liveLots, lotStates, portfolio, valueLots } from "./metals"
import { cashInBase, debtBalances, liveMonthCloses } from "./networth"
import { db, type MemberContribution, type MonthClose, type NetWorthSnapshot, type Visibility } from "./schema"
import { writeFields } from "./write"
import { todayIso } from "@/domain/calendar/month"
import { netWorth, type NetWorthInput, type NetWorthResult } from "@/domain/networth/networth"
import { cachedQuotes, liveOverrides, liveSetting, resolvePrices } from "@/market/store"

type Owned = { visibility: Visibility; ownerId: string | null }
type RowFilter = (row: Owned) => boolean

export interface Figure {
  input: NetWorthInput
  rateSet: Record<string, unknown>
}

/** The same figure the net worth page shows, over whichever rows the filter keeps. */
export async function figureOver(session: LocalSession, keep: RowFilter): Promise<Figure> {
  const [accounts, debts, payments, assets, valuations, lots, disposalLots, quotes, overrides, setting] = await Promise.all([
    liveCashAccounts(), liveDebts(), livePayments(), liveAssets(), liveValuations(), liveLots(), liveDisposalLots(), cachedQuotes(), liveOverrides(), liveSetting(),
  ])
  const prices = resolvePrices(quotes, overrides, setting)
  const keptLots = lots.filter(keep)
  const lotIds = new Set(keptLots.map((lot) => lot.id))
  const metals = portfolio(valueLots(lotStates(keptLots, disposalLots.filter((d) => lotIds.has(d.lotId))), prices, setting))
    .reduce((sum, line) => sum + line.valueNow, 0)
  const keptDebts = debts.filter(keep)
  const debtIds = new Set(keptDebts.map((debt) => debt.id))
  const { receivables, liabilities } = debtBalances(keptDebts, payments.filter((p) => debtIds.has(p.debtId)))
  const keptAssets = assets.filter(keep)
  const assetIds = new Set(keptAssets.map((asset) => asset.id))
  const totals = assetTotals(keptAssets, valuations.filter((v) => assetIds.has(v.assetId)), todayIso())
  return {
    input: { cash: cashInBase(accounts.filter(keep), session.baseCurrency, prices), metals, receivables, ...totals, liabilities },
    rateSet: { rateKind: prices.rateKind, usdIqdMicros: prices.usdIqdMicros, usdIqdSource: prices.rateSource.label, xauSource: prices.spot.GOLD.source.label, xagSource: prices.spot.SILVER.source.label },
  }
}

const mine = (session: LocalSession): RowFilter => (row) => row.visibility === "PRIVATE" && (row.ownerId === session.userId || row.ownerId === null)

function inputOf(composition: MemberContribution["composition"], liabilities: number): NetWorthInput {
  const amount = (cls: string) => composition.shares.find((share) => share.assetClass === cls)?.amount ?? 0
  return {
    cash: amount("CASH"), metals: amount("METALS"), receivables: amount("RECEIVABLES"), vehicles: amount("VEHICLES"),
    property: amount("PROPERTY"), otherAssets: amount("OTHER_ASSETS"), illiquid: composition.illiquidAssets, liabilities,
  }
}

function add(a: NetWorthInput, b: NetWorthInput): NetWorthInput {
  return {
    cash: a.cash + b.cash, metals: a.metals + b.metals, receivables: a.receivables + b.receivables, vehicles: a.vehicles + b.vehicles,
    property: a.property + b.property, otherAssets: a.otherAssets + b.otherAssets, illiquid: a.illiquid + b.illiquid, liabilities: a.liabilities + b.liabilities,
  }
}

export const liveContributions = () => db.memberContributions.filter((c) => c.deletedAt === null).toArray()

/**
 * BR-16: the household figure counts every member's private holdings without itemising them.
 * This device sees shared rows and its owner's private ones; every other member's private
 * part arrives as that member's sealed contribution.
 */
export async function householdFigure(session: LocalSession): Promise<{ result: NetWorthResult; rateSet: Record<string, unknown> }> {
  const visible = await figureOver(session, () => true)
  const others = (await liveContributions()).filter((c) => c.memberId !== session.userId)
  const input = others.reduce((sum, c) => add(sum, inputOf(c.composition, c.totalLiabilities)), visible.input)
  return { result: netWorth(input), rateSet: visible.rateSet }
}

/** Rewritten only when the totals move, so an idle device adds nothing to the outbox. */
export async function publishMyContribution(session: LocalSession) {
  const { input } = await figureOver(session, mine(session))
  const result = netWorth(input)
  const id = await deterministicId("member_contribution", session.householdId, session.userId)
  const composition = { shares: result.composition, illiquidAssets: result.illiquidAssets }
  const existing = await db.memberContributions.get(id)
  if (existing && existing.deletedAt === null && existing.totalAssets === result.totalAssets && existing.totalLiabilities === result.totalLiabilities
    && JSON.stringify(existing.composition) === JSON.stringify(composition)) return
  await writeFields<MemberContribution>("member_contribution", id, {
    ...(existing ? {} : { visibility: "SHARED" as const }),
    memberId: session.userId, totalAssets: result.totalAssets, totalLiabilities: result.totalLiabilities, composition,
    computedAt: new Date().toISOString(), deletedAt: null,
  })
}

/** BR-14: closing takes an append-only snapshot; reopening is explicit; closing again takes a fresh one. */
export async function closeMonth(session: LocalSession, monthKey: string) {
  const closes = await liveMonthCloses()
  const existing = closes.find((c) => c.monthKey === monthKey)
  if (existing && existing.closedAt !== null && existing.reopenedAt === null) return
  const { result, rateSet } = await householdFigure(session)
  const now = new Date().toISOString()
  const snapshotId = crypto.randomUUID()
  await writeFields<NetWorthSnapshot>("net_worth_snapshot", snapshotId, {
    visibility: "SHARED", monthKey, takenAt: now, totalAssets: result.totalAssets, totalLiabilities: result.totalLiabilities,
    netWorth: result.netWorth, liquidAssets: result.liquidAssets, illiquidAssets: result.illiquidAssets, composition: result.composition, rateSet,
  })
  const closeId = existing?.id ?? (await deterministicId("month_close", session.householdId, monthKey))
  await writeFields<MonthClose>("month_close", closeId, {
    ...(existing ? {} : { visibility: "SHARED" as const, monthKey }), snapshotId, closedAt: now, reopenedAt: null,
  })
}

export async function reopenMonth(monthKey: string) {
  const close = (await liveMonthCloses()).find((c) => c.monthKey === monthKey)
  if (!close || close.closedAt === null || close.reopenedAt !== null) return
  await writeFields<MonthClose>("month_close", close.id, { reopenedAt: new Date().toISOString() })
}
