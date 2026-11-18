import type { LocalSession } from "@/api/auth"
import { api, unwrap } from "@/api/client"
import { mayWriteRecords, mayWriteShared } from "@/app/roles"
import { billsDue, liveOccurrencesFor, liveRecurringExpenses } from "@/db/expenses"
import { closeMonth, publishMyContribution } from "@/db/householdFigure"
import { readMeta, writeMeta } from "@/db/meta"
import { liveMonthCloses, liveSnapshots, isMonthClosed, snapshotFor } from "@/db/networth"
import { NOTIFICATION_DEFAULTS, liveNotificationSetting } from "@/db/notifications"
import { db, localTableFor, SYNC_TABLES, type AnnualReport, type AnnualReportMonth, type Bucket, type Plan, type Syncable, type SyncTableName } from "@/db/schema"
import { payloadOf, writeFields } from "@/db/write"
import { addDays, monthKeyFor, monthWindow, shiftKey, todayIso } from "@/domain/calendar/month"
import { monthViewFor } from "@/features/plan/useMonthFigures"
import { deterministicId } from "@/vault/crypto"
import { holdsKey } from "@/vault/keyring"

const APPEND_ONLY: ReadonlySet<string> = new Set(["net_worth_snapshot"])

/** One failing duty must not stop the others or the sync around them. */
async function attempt(name: string, duty: () => Promise<void>) {
  try {
    await duty()
  } catch (error) {
    console.warn(`Sync duty ${name} failed`, error)
  }
}

export async function runDeviceDuties(session: LocalSession) {
  if (mayWriteRecords(session.role)) await attempt("migrate", () => migrateLegacyRows(session))
  if (!mayWriteShared(session.role) || !holdsKey("HOUSEHOLD")) {
    await attempt("schedule", () => uploadReminderSchedule(session))
    return
  }
  if (session.role === "OWNER") await attempt("seed", () => seedDefaultPlan(session))
  await attempt("contribution", () => publishMyContribution(session))
  await attempt("auto-close", () => closeEndedMonth(session))
  if (holdsKey("REPORT")) await attempt("reports", () => publishAnnualReports(session))
  await attempt("schedule", () => uploadReminderSchedule(session))
}

const migratedKey = (session: LocalSession) => `vault.migrated.${session.householdId}.${session.userId}`

/**
 * ADR e2e-1: the rows this device is answerable for (its owner's, and every shared row when
 * the owner signs in) are re-sent sealed; the server then deletes the plain copies.
 */
async function migrateLegacyRows(session: LocalSession) {
  if (await readMeta<boolean>(migratedKey(session))) return
  const status = await unwrap(api.GET("/api/v1/vault/status"))
  if (status.migrated || status.legacyRows === 0) {
    await writeMeta(migratedKey(session), true)
    return
  }
  const isOwner = session.role === "OWNER"
  if (isOwner && !holdsKey("HOUSEHOLD")) return
  for (const table of Object.keys(SYNC_TABLES) as SyncTableName[]) {
    const rows = (await localTableFor(table).toArray()) as (Syncable & { visibility?: string })[]
    for (const row of rows) {
      if (row.vaulted) continue
      const answerable = row.ownerId === session.userId || (isOwner && row.visibility !== "PRIVATE")
      if (!answerable) continue
      if (row.visibility !== "PRIVATE" && !holdsKey("HOUSEHOLD")) continue
      if (APPEND_ONLY.has(table) && (await db.outbox.where("rowId").equals(row.id).count()) > 0) continue
      await writeFields(table, row.id, payloadOf(row) as never)
    }
  }
  await writeMeta(`${migratedKey(session)}.pending`, true)
}

/** Called once the outbox has drained, so the server only drops plain rows it already holds sealed. */
export async function completeMigrationIfDrained(session: LocalSession) {
  if (!(await readMeta<boolean>(`${migratedKey(session)}.pending`))) return
  if ((await db.outbox.count()) > 0) return
  await unwrap(api.POST("/api/v1/vault/migration/complete"))
  await writeMeta(migratedKey(session), true)
  await writeMeta(`${migratedKey(session)}.pending`, false)
}

const DEFAULT_BUCKETS = [
  { name: "Essentials", colour: "#88aaee", shareBasisPoints: 5500 },
  { name: "Guilt-free", colour: "#ffdc58", shareBasisPoints: 500 },
  { name: "Debt & invest", colour: "#ff6b6b", shareBasisPoints: 1000 },
  { name: "Short-term savings", colour: "#a3e635", shareBasisPoints: 1500 },
  { name: "Investing", colour: "#c4a1ff", shareBasisPoints: 1500 },
]

/** FR-PLN-02: a new household starts with the default split; fixed ids keep two devices from seeding twice. */
async function seedDefaultPlan(session: LocalSession) {
  if ((await db.plans.count()) > 0) return
  const planId = await deterministicId("default_plan", session.householdId)
  await writeFields<Plan>("plan", planId, { visibility: "SHARED", effectiveFrom: monthKeyFor(todayIso(), session.monthStartDay), effectiveTo: null })
  for (const [order, bucket] of DEFAULT_BUCKETS.entries()) {
    await writeFields<Bucket>("bucket", await deterministicId("default_bucket", session.householdId, String(order)), {
      visibility: "SHARED", planId, name: bucket.name, colour: bucket.colour, shareBasisPoints: bucket.shareBasisPoints,
      fixedAmount: null, carryOver: false, sortOrder: order,
    })
  }
}

/** FR-NET-03: the month that just ended is closed by the first device to sync after it. */
async function closeEndedMonth(session: LocalSession) {
  const ended = shiftKey(monthKeyFor(todayIso(), session.monthStartDay), -1)
  const firstPlan = (await db.plans.orderBy("effectiveFrom").first())?.effectiveFrom
  if (!firstPlan || ended < firstPlan) return
  if (isMonthClosed(ended, await liveMonthCloses())) return
  if ((await db.monthCloses.filter((c) => c.monthKey === ended).count()) > 0) return
  await closeMonth(session, ended)
}

const PERCENT_SCALE = 10

/** FR-RPT-05: (received − spent) ÷ received, one decimal place, half-up; null when nothing came in. */
export function savingRate(received: number, spent: number): number | null {
  if (received <= 0) return null
  return Math.floor(((received - spent) * 100 * PERCENT_SCALE * 2 + received) / (2 * received)) / PERCENT_SCALE
}

const YEARS_PUBLISHED = 2

/** §4.2 P4: totals over shared records only, sealed under the report key an advisor holds. */
async function publishAnnualReports(session: LocalSession) {
  const thisYear = new Date().getFullYear()
  const [receipts, transactions, closes, snapshots] = await Promise.all([
    db.incomeReceipts.filter((r) => r.deletedAt === null && r.visibility === "SHARED").toArray(),
    db.transactions.filter((t) => t.deletedAt === null && t.visibility === "SHARED" && t.type === "EXPENSE").toArray(),
    liveMonthCloses(), liveSnapshots(),
  ])
  for (let year = thisYear - YEARS_PUBLISHED + 1; year <= thisYear; year++) {
    const months: AnnualReportMonth[] = Array.from({ length: 12 }, (_, i) => {
      const monthKey = `${year}-${String(i + 1).padStart(2,"0")}`
      const received = receipts.filter((r) => r.monthKey === monthKey).reduce((s, r) => s + r.baseAmount, 0)
      const spent = transactions.filter((t) => t.monthKey === monthKey).reduce((s, t) => s + t.baseAmount, 0)
      return { monthKey, received, spent, saved: received - spent, savingRate: savingRate(received, spent) }
    })
    const received = months.reduce((s, m) => s + m.received, 0)
    const spent = months.reduce((s, m) => s + m.spent, 0)
    const yearSnapshots = months
      .filter((m) => isMonthClosed(m.monthKey, closes))
      .map((m) => ({ monthKey: m.monthKey, snapshot: snapshotFor(m.monthKey, closes, snapshots) }))
      .flatMap(({ monthKey, snapshot }) => (snapshot ? [{ monthKey, netWorth: snapshot.netWorth }] : []))
    const report: AnnualReport["report"] = { baseCurrency: session.baseCurrency, months, received, spent, savingRate: savingRate(received, spent), snapshots: yearSnapshots }
    const id = await deterministicId("annual_report", session.householdId, String(year))
    const existing = await db.annualReports.get(id)
    if (existing && JSON.stringify(existing.report) === JSON.stringify(report)) continue
    await writeFields<AnnualReport>("annual_report", id, { ...(existing ? {} : { visibility: "SHARED" as const }), year, report, computedAt: new Date().toISOString() })
  }
}

type ReminderKind = "BILL_DUE" | "PAY_DAY" | "OVERSPEND" | "MONTH_CLOSE"
interface Reminder {
  kind: ReminderKind
  fireAt: string
  dedupeKey: string
}

const SCHEDULE_HASH = "notifications.scheduleHash"
const SCHEDULE_AT = "notifications.scheduleAt"
const SCHEDULE_REFRESH_MS = 6 * 60 * 60 * 1000
const REMINDER_HOUR = 9
const MONTH_END_HOUR = 18
const BASIS_POINTS = 10_000

const at = (date: string, hour: number) => new Date(`${date}T${String(hour).padStart(2,"0")}:00:00`).toISOString()

/**
 * ADR e2e-1: the server can no longer read bills or budgets, so this device works out when a
 * reminder is due and hands over only its kind and time; the text the server sends is generic.
 */
async function reminders(session: LocalSession): Promise<{ items: Reminder[]; metalPriceAlerts: boolean; metalMoveBasisPoints: number }> {
  const setting = (await liveNotificationSetting()) ?? NOTIFICATION_DEFAULTS
  const holdsMetal = (await db.metalLots.filter((lot) => lot.deletedAt === null).count()) > 0
  const base = { metalPriceAlerts: setting.metalPrice && !setting.quietMode && holdsMetal, metalMoveBasisPoints: setting.metalMoveBp }
  if (setting.quietMode) return { items: [], ...base }
  const today = todayIso()
  const now = Date.now()
  const monthKey = monthKeyFor(today, session.monthStartDay)
  const soonest = (iso: string) => (Date.parse(iso) < now ? new Date(now).toISOString() : iso)
  const items: Reminder[] = []

  if (setting.billDue) {
    const expenses = await liveRecurringExpenses()
    for (const key of [monthKey, shiftKey(monthKey, 1)]) {
      for (const bill of billsDue(expenses, await liveOccurrencesFor(key), key, session.monthStartDay)) {
        if (bill.occurrence || bill.dueDate < today) continue
        const lead = bill.expense.reminderDays ?? setting.billLeadDays
        items.push({ kind: "BILL_DUE", fireAt: soonest(at(addDays(bill.dueDate, -lead), REMINDER_HOUR)), dedupeKey: `bill:${bill.expense.id}:${bill.dueDate}` })
      }
    }
  }

  const view = setting.payDay || setting.overspend ? await monthViewFor(monthKey, session.monthStartDay) : null
  if (view && setting.payDay) {
    for (const occurrence of view.occurrences) {
      if (occurrence.receipt || occurrence.date < today) continue
      items.push({ kind: "PAY_DAY", fireAt: at(occurrence.date, REMINDER_HOUR), dedupeKey: `pay:${occurrence.source.id}:${occurrence.date}` })
    }
  }
  if (view && setting.overspend) {
    for (const figure of view.figures.buckets) {
      if (figure.allocated <= 0 || figure.spent * BASIS_POINTS < figure.allocated * setting.overspendThresholdBp) continue
      items.push({ kind: "OVERSPEND", fireAt: new Date(now).toISOString(), dedupeKey: `over:${figure.id}:${monthKey}` })
    }
  }

  if (setting.monthClose && !isMonthClosed(monthKey, await liveMonthCloses())) {
    const lastDay = addDays(monthWindow(monthKey, session.monthStartDay).toExclusive, -1)
    items.push({ kind: "MONTH_CLOSE", fireAt: at(lastDay, MONTH_END_HOUR), dedupeKey: `close:${monthKey}` })
  }
  return { items, ...base }
}

async function uploadReminderSchedule(session: LocalSession) {
  const schedule = await reminders(session)
  const hash = JSON.stringify(schedule)
  const lastAt = (await readMeta<number>(SCHEDULE_AT)) ?? 0
  if (hash === (await readMeta<string>(SCHEDULE_HASH)) && Date.now() - lastAt < SCHEDULE_REFRESH_MS) return
  await unwrap(api.PUT("/api/v1/notifications/schedule", { body: schedule }))
  await writeMeta(SCHEDULE_HASH, hash)
  await writeMeta(SCHEDULE_AT, Date.now())
}
