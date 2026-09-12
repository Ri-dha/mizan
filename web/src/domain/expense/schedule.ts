import { addDays, daysBetween, daysInMonth, inWindow, shiftKey, type MonthWindow } from "@/domain/calendar/month"

export type ExpenseFrequency = "MONTHLY" | "QUARTERLY" | "ANNUAL" | "CUSTOM"

export interface Schedule {
  frequency: ExpenseFrequency
  dueDay: number | null
  anchorDate: string | null
  intervalDays: number | null
  activeFrom: string
  activeTo: string | null
}

const QUARTER_MONTHS = 3
const YEAR_MONTHS = 12

/** Due dates of a bill inside a month (FR-EXP-01): monthly by day, longer cycles from an anchor. */
export function dueDates(schedule: Schedule, window: MonthWindow): string[] {
  return candidateDates(schedule, window).filter(
    (date) => date >= schedule.activeFrom && (schedule.activeTo === null || date <= schedule.activeTo),
  )
}

function candidateDates(schedule: Schedule, window: MonthWindow): string[] {
  switch (schedule.frequency) {
    case "MONTHLY":
      return monthlyDates(schedule.dueDay ?? 1, window)
    case "QUARTERLY":
      return monthStepDates(schedule.anchorDate!, QUARTER_MONTHS, window)
    case "ANNUAL":
      return monthStepDates(schedule.anchorDate!, YEAR_MONTHS, window)
    case "CUSTOM":
      return cycleDates(schedule.anchorDate!, Math.max(1, schedule.intervalDays ?? 1), window)
  }
}

function iso(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
}

function monthlyDates(dueDay: number, window: MonthWindow): string[] {
  const dates: string[] = []
  let key = window.from.slice(0, 7)
  const lastKey = addDays(window.toExclusive, -1).slice(0, 7)
  while (key <= lastKey) {
    const [year, month] = key.split("-").map(Number)
    const candidate = iso(year, month, Math.min(dueDay, daysInMonth(year, month)))
    if (inWindow(window, candidate)) dates.push(candidate)
    key = shiftKey(key, 1)
  }
  return dates
}

function monthStepDates(anchor: string, stepMonths: number, window: MonthWindow): string[] {
  const [anchorYear, anchorMonth, anchorDay] = anchor.split("-").map(Number)
  const dates: string[] = []
  let key = `${String(anchorYear).padStart(4, "0")}-${String(anchorMonth).padStart(2, "0")}`
  const lastKey = addDays(window.toExclusive, -1).slice(0, 7)
  while (key <= lastKey) {
    const [year, month] = key.split("-").map(Number)
    const candidate = iso(year, month, Math.min(anchorDay, daysInMonth(year, month)))
    if (inWindow(window, candidate)) dates.push(candidate)
    key = shiftKey(key, stepMonths)
  }
  return dates
}

function cycleDates(anchor: string, stepDays: number, window: MonthWindow): string[] {
  let date = anchor
  if (anchor < window.from) {
    date = addDays(anchor, Math.ceil(daysBetween(anchor, window.from) / stepDays) * stepDays)
  }
  const dates: string[] = []
  while (date < window.toExclusive) {
    dates.push(date)
    date = addDays(date, stepDays)
  }
  return dates
}
