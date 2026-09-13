import { liveMonthCloses, liveSnapshots, trendPoints } from "./networth"
import type { LedgerTransaction } from "./schema"
import { monthViewFor, type MonthView } from "@/features/plan/useMonthFigures"
import { shiftKey } from "@/domain/calendar/month"

export interface MonthSummary {
  key: string
  received: number
  spent: number
  saved: number
  /** One decimal place; null when nothing was received. */
  savingRate: number | null
  byBucket: Record<string, number>
  byCategory: Record<string, number>
  bucketNames: Record<string, string>
}

export interface YearSummary {
  months: MonthSummary[]
  received: number
  spent: number
  saved: number
  savingRate: number | null
  byCategory: Record<string, number>
  netWorthStart: number | null
  netWorthEnd: number | null
}

const PERCENT_SCALE = 10

/** FR-RPT-05: saving rate = (received − spent) ÷ received, one decimal place, half-up. */
export function savingRate(received: number, spent: number): number | null {
  if (received <= 0) return null
  return Math.round(((received - spent) * 100 * PERCENT_SCALE) / received) / PERCENT_SCALE
}

export function spentByCategory(transactions: LedgerTransaction[], uncategorised: string): Record<string, number> {
  const totals: Record<string, number> = {}
  for (const tx of transactions) {
    if (tx.type !== "EXPENSE") continue
    const key = tx.category ?? uncategorised
    totals[key] = (totals[key] ?? 0) + tx.baseAmount
  }
  return totals
}

export function summarise(view: MonthView, uncategorised: string): MonthSummary {
  const spent = view.figures.buckets.reduce((s, b) => s + b.spent, 0)
  return {
    key: view.figures.buckets.length || view.income.received ? view.plan?.effectiveFrom ?? "" : "",
    received: view.income.received,
    spent,
    saved: view.income.received - spent,
    savingRate: savingRate(view.income.received, spent),
    byBucket: Object.fromEntries(view.figures.buckets.map((b) => [b.id, b.spent])),
    byCategory: spentByCategory(view.transactions, uncategorised),
    bucketNames: Object.fromEntries(view.buckets.map((b) => [b.id, b.name])),
  }
}

/** FR-RPT-03/04: the months of a calendar year, oldest first, with net worth from the closing snapshots. */
export async function yearSummary(year: number, startDay: number, uncategorised: string): Promise<YearSummary> {
  const months: MonthSummary[] = []
  for (let m = 1; m <= 12; m++) {
    const key = `${year}-${String(m).padStart(2, "0")}`
    months.push({ ...summarise(await monthViewFor(key, startDay), uncategorised), key })
  }
  const received = months.reduce((s, m) => s + m.received, 0)
  const spent = months.reduce((s, m) => s + m.spent, 0)
  const byCategory: Record<string, number> = {}
  for (const month of months) for (const [k, v] of Object.entries(month.byCategory)) byCategory[k] = (byCategory[k] ?? 0) + v

  const [closes, snapshots] = await Promise.all([liveMonthCloses(), liveSnapshots()])
  const points = trendPoints(closes, snapshots)
  const before = points.filter((p) => p.monthKey < `${year}-01`).at(-1)
  const inYear = points.filter((p) => p.monthKey.startsWith(String(year)))
  return {
    months, received, spent, saved: received - spent, savingRate: savingRate(received, spent), byCategory,
    netWorthStart: (before ?? inYear[0])?.netWorth ?? null,
    netWorthEnd: inYear.at(-1)?.netWorth ?? null,
  }
}

/** Trailing twelve months ending at a month key, for the rolling saving rate. */
export async function trailingSavingRate(endKey: string, startDay: number): Promise<number | null> {
  let received = 0
  let spent = 0
  for (let i = 11; i >= 0; i--) {
    const view = await monthViewFor(shiftKey(endKey, -i), startDay)
    received += view.income.received
    spent += view.figures.buckets.reduce((s, b) => s + b.spent, 0)
  }
  return savingRate(received, spent)
}
