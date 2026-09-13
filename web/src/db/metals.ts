import { db, type DisposalMethod, type MarketSetting, type Metal, type MetalDisposal, type MetalDisposalLot, type MetalLot, type Visibility } from "./schema"
import { softDelete, writeFields } from "./write"
import { toBaseAmount } from "./income"
import { planDisposal, type PlannerLot } from "@/domain/metal/disposal"
import { metalValuation, valueOf } from "@/domain/metal/valuation"
import type { ResolvedPrices } from "@/market/store"

export interface LotInput {
  metal: Metal
  purityLabel: string
  purityBasisPoints: number
  weightMg: number
  weightUnitEntered: MetalLot["weightUnitEntered"]
  quantityEntered: string
  purchaseDate: string
  metalCost: number
  makingCharge: number
  fees: number
  currency: string
  fxRateMicros: number
  form: MetalLot["form"]
  dealer: string | null
  location: string | null
  serial: string | null
  heldFor: string | null
  note: string | null
  visibility: Visibility
}

export async function createLot(input: LotInput): Promise<string> {
  const id = crypto.randomUUID()
  await writeFields<MetalLot>("metal_lot", id, input)
  return id
}

export async function updateLot(id: string, input: Partial<LotInput>) {
  await writeFields<MetalLot>("metal_lot", id, input)
}

export const removeLot = (id: string) => softDelete("metal_lot", id)

export const liveLots = () => db.metalLots.filter((l) => l.deletedAt === null).sortBy("purchaseDate")
export const liveDisposals = () => db.metalDisposals.filter((d) => d.deletedAt === null).reverse().sortBy("soldOn")
export const liveDisposalLots = () => db.metalDisposalLots.filter((d) => d.deletedAt === null).toArray()

export interface LotState {
  lot: MetalLot
  remainingMg: number
  costBasisBase: number
  metalCostBase: number
  makingBase: number
  feesBase: number
  costPerGramBase: number
}

/** What is left of each lot after its sales, with the cost basis that remains in base currency (BR-12). */
export function lotStates(lots: MetalLot[], disposalLots: MetalDisposalLot[]): LotState[] {
  return lots.map((lot) => {
    const sold = disposalLots.filter((d) => d.lotId === lot.id)
    const soldMg = sold.reduce((s, d) => s + d.weightMg, 0)
    const metalCostBase = toBaseAmount(lot.metalCost, lot.fxRateMicros) - sold.reduce((s, d) => s + d.metalCost, 0)
    const makingBase = toBaseAmount(lot.makingCharge, lot.fxRateMicros) - sold.reduce((s, d) => s + d.makingCharge, 0)
    const feesBase = toBaseAmount(lot.fees, lot.fxRateMicros) - sold.reduce((s, d) => s + d.fees, 0)
    const remainingMg = lot.weightMg - soldMg
    const costBasisBase = metalCostBase + makingBase + feesBase
    return {
      lot, remainingMg, costBasisBase, metalCostBase, makingBase, feesBase,
      costPerGramBase: remainingMg > 0 ? Math.round((costBasisBase * 1000) / remainingMg) : 0,
    }
  })
}

export interface LotValuation extends LotState {
  perGramNowBase: number
  valueNow: number
  gain: number
  gainExcludingMaking: number
  gainPercent: number | null
}

export function premiumFor(setting: MarketSetting | undefined, metal: Metal): number {
  return metal === "GOLD" ? (setting?.goldPremiumBasisPoints ?? 0) : (setting?.silverPremiumBasisPoints ?? 0)
}

/** FR-MTL-07: each open lot at today's price, against what it cost. */
export function valueLots(states: LotState[], prices: ResolvedPrices, setting: MarketSetting | undefined): LotValuation[] {
  return states.map((state) => {
    const { lot } = state
    const perGramMicros = perGramFor(lot.metal, lot.purityBasisPoints, prices, setting)
    const valueNow = valueOf(perGramMicros, state.remainingMg)
    const gain = valueNow - state.costBasisBase
    return {
      ...state,
      perGramNowBase: Math.round(perGramMicros / 1_000_000),
      valueNow,
      gain,
      gainExcludingMaking: valueNow - (state.metalCostBase + state.feesBase),
      gainPercent: state.costBasisBase > 0 ? Math.round((gain * 1000) / state.costBasisBase) / 10 : null,
    }
  })
}

/** IQD micros per gram at a purity, through the shared BR-08 maths; a user-entered price replaces spot × rate. */
export function perGramFor(metal: Metal, purityBasisPoints: number, prices: ResolvedPrices, setting: MarketSetting | undefined): number {
  const spot = prices.spot[metal]
  return metalValuation({
    spotUsdPerOzMicros: spot.spotUsdPerOzMicros, usdIqdMicros: prices.usdIqdMicros, purityBasisPoints,
    premiumBasisPoints: premiumFor(setting, metal), premiumFixedPerGramMicros: 0, weightMg: 0,
    overridePerGram24kMicros: spot.perGram24kOverrideMicros,
  }).perGramMicros
}

export interface PortfolioLine {
  metal: Metal
  purityLabel: string
  weightMg: number
  costBasis: number
  valueNow: number
  gain: number
  gainExcludingMaking: number
}

/** FR-MTL-06: aggregates reconcile to the sum of open lots by construction. */
export function portfolio(valued: LotValuation[]): PortfolioLine[] {
  const lines = new Map<string, PortfolioLine>()
  for (const v of valued) {
    if (v.remainingMg <= 0) continue
    const key = `${v.lot.metal}:${v.lot.purityLabel}`
    const line = lines.get(key) ?? { metal: v.lot.metal, purityLabel: v.lot.purityLabel, weightMg: 0, costBasis: 0, valueNow: 0, gain: 0, gainExcludingMaking: 0 }
    line.weightMg += v.remainingMg
    line.costBasis += v.costBasisBase
    line.valueNow += v.valueNow
    line.gain += v.gain
    line.gainExcludingMaking += v.gainExcludingMaking
    lines.set(key, line)
  }
  return [...lines.values()]
}

export interface SaleInput {
  metal: Metal
  weightMg: number
  proceeds: number
  fees: number
  currency: string
  fxRateMicros: number
  soldOn: string
  method: DisposalMethod
  specificLotIds: string[]
  buyer: string | null
  note: string | null
}

/** FR-MTL-05: plans the draw-down, then records the sale and one row per lot it came from. */
export async function recordSale(states: LotState[], input: SaleInput) {
  const candidates: PlannerLot[] = states
    .filter((s) => s.lot.metal === input.metal && s.remainingMg > 0)
    .map((s) => ({ id: s.lot.id, purchaseDate: s.lot.purchaseDate, weightMg: s.lot.weightMg, remainingMg: s.remainingMg, metalCost: s.metalCostBase, makingCharge: s.makingBase, fees: s.feesBase }))
  const proceedsBase = toBaseAmount(input.proceeds - input.fees, input.fxRateMicros)
  const plan = planDisposal(candidates, input.weightMg, input.method, input.specificLotIds, proceedsBase)

  await db.transaction("rw", db.tables, async () => {
    const disposalId = crypto.randomUUID()
    await writeFields<MetalDisposal>("metal_disposal", disposalId, {
      visibility: "SHARED", metal: input.metal, soldOn: input.soldOn, weightMg: input.weightMg, proceeds: input.proceeds, fees: input.fees,
      currency: input.currency, fxRateMicros: input.fxRateMicros, method: input.method, buyer: input.buyer, note: input.note,
    })
    for (const allocation of plan.allocations) {
      await writeFields<MetalDisposalLot>("metal_disposal_lot", crypto.randomUUID(), {
        visibility: "SHARED", disposalId, lotId: allocation.lotId, weightMg: allocation.weightMg,
        metalCost: allocation.metalCost, makingCharge: allocation.makingCharge, fees: allocation.fees,
      })
    }
  })
  return plan
}

export async function undoSale(disposal: MetalDisposal, disposalLots: MetalDisposalLot[]) {
  await db.transaction("rw", db.tables, async () => {
    for (const row of disposalLots.filter((d) => d.disposalId === disposal.id)) await softDelete("metal_disposal_lot", row.id)
    await softDelete("metal_disposal", disposal.id)
  })
}

export function realisedGain(disposal: MetalDisposal, disposalLots: MetalDisposalLot[]) {
  const rows = disposalLots.filter((d) => d.disposalId === disposal.id)
  const proceeds = toBaseAmount(disposal.proceeds - disposal.fees, disposal.fxRateMicros)
  const costMetal = rows.reduce((s, r) => s + r.metalCost, 0)
  const costMaking = rows.reduce((s, r) => s + r.makingCharge, 0)
  const costFees = rows.reduce((s, r) => s + r.fees, 0)
  return { proceeds, gain: proceeds - costMetal - costMaking - costFees, gainExcludingMaking: proceeds - costMetal - costFees }
}
