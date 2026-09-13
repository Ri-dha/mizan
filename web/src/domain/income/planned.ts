import { addDays, daysBetween, daysInMonth, inWindow, type MonthWindow } from "@/domain/calendar/month"

export type Frequency = "MONTHLY" | "BIWEEKLY" | "WEEKLY" | "ONE_OFF"

/** One entry of a source's amount history: what it paid from a given date. */
export interface AmountStep {
  effectiveFrom: string
  baseAmount: number
}

export interface PlannedSource {
  baseAmount: number
  frequency: Frequency
  payDay: number | null
  anchorDate: string | null
  activeFrom: string
  activeTo: string | null
  amountSteps: AmountStep[]
}

/** The amount in force on a pay date; sources without a history fall back to their current amount. */
export function amountOn(source: Pick<PlannedSource, "baseAmount" | "amountSteps">, date: string): number {
  const step = source.amountSteps.filter((s) => s.effectiveFrom <= date).sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? 1 : -1))[0]
  return step ? step.baseAmount : source.baseAmount
}

export interface Occurrence {
  sourceIndex: number
  date: string
  amount: number
}

export interface PlannedIncomeResult {
  total: number
  occurrences: Occurrence[]
}

const WEEK_DAYS = 7
const TWO_WEEKS_DAYS = 14

/** Which pay dates fall inside a month, and what they are worth in the base currency (FR-INC-03). */
export function plannedIncome(sources: PlannedSource[], window: MonthWindow): PlannedIncomeResult {
  const occurrences: Occurrence[] = []
  sources.forEach((source, sourceIndex) => {
    for (const date of payDates(source, window)) {
      if (isActiveOn(source, date)) occurrences.push({ sourceIndex, date, amount: amountOn(source, date) })
    }
  })
  occurrences.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.sourceIndex - b.sourceIndex))
  return { total: occurrences.reduce((sum, o) => sum + o.amount, 0), occurrences }
}

function isActiveOn(source: PlannedSource, date: string): boolean {
  return date >= source.activeFrom && (source.activeTo === null || date <= source.activeTo)
}

function payDates(source: PlannedSource, window: MonthWindow): string[] {
  switch (source.frequency) {
    case "MONTHLY":
      return monthlyDates(source.payDay ?? 1, window)
    case "WEEKLY":
      return cycleDates(source.anchorDate!, WEEK_DAYS, window)
    case "BIWEEKLY":
      return cycleDates(source.anchorDate!, TWO_WEEKS_DAYS, window)
    case "ONE_OFF":
      return source.anchorDate && inWindow(window, source.anchorDate) ? [source.anchorDate] : []
  }
}

// A window spans at most two calendar months; the pay day exists once in each.
function monthlyDates(payDay: number, window: MonthWindow): string[] {
  const dates: string[] = []
  const [fromYear, fromMonth] = window.from.split("-").map(Number)
  const lastDay = addDays(window.toExclusive, -1)
  const [lastYear, lastMonth] = lastDay.split("-").map(Number)
  let year = fromYear
  let month = fromMonth
  while (year < lastYear || (year === lastYear && month <= lastMonth)) {
    const day = Math.min(payDay, daysInMonth(year, month))
    const candidate = `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
    if (inWindow(window, candidate)) dates.push(candidate)
    month += 1
    if (month > 12) {
      month = 1
      year += 1
    }
  }
  return dates
}

function cycleDates(anchor: string, stepDays: number, window: MonthWindow): string[] {
  let date = anchor
  if (anchor < window.from) {
    const cycles = Math.ceil(daysBetween(anchor, window.from) / stepDays)
    date = addDays(anchor, cycles * stepDays)
  }
  const dates: string[] = []
  while (date < window.toExclusive) {
    dates.push(date)
    date = addDays(date, stepDays)
  }
  return dates
}
