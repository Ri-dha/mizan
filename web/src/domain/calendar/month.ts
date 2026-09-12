/**
 * A household month (FR-SET-05): starts on the configured day of the calendar month named by
 * its key and ends the day before that day in the next one. Keyed by the calendar month it
 * starts in, so "2026-09" with start day 25 runs 25 Sep to 24 Oct. Dates are "YYYY-MM-DD".
 */
export interface MonthWindow {
  key: string
  from: string
  toExclusive: string
}

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

function isoDate(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
}

function parseKey(key: string): { year: number; month: number } {
  const [year, month] = key.split("-").map(Number)
  return { year, month }
}

function keyOf(year: number, month: number): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}`
}

function startOf(year: number, month: number, startDay: number): string {
  return isoDate(year, month, Math.min(startDay, daysInMonth(year, month)))
}

export function shiftKey(key: string, months: number): string {
  const { year, month } = parseKey(key)
  const index = year * 12 + (month - 1) + months
  return keyOf(Math.floor(index / 12), (index % 12) + 1)
}

export function monthWindow(key: string, startDay: number): MonthWindow {
  const { year, month } = parseKey(key)
  const next = shiftKey(key, 1)
  const { year: nextYear, month: nextMonth } = parseKey(next)
  return { key, from: startOf(year, month, startDay), toExclusive: startOf(nextYear, nextMonth, startDay) }
}

export function monthKeyFor(date: string, startDay: number): string {
  const [year, month] = date.split("-").map(Number)
  const key = keyOf(year, month)
  return date < startOf(year, month, startDay) ? shiftKey(key, -1) : key
}

export function todayIso(now = new Date()): string {
  return isoDate(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return isoDate(shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate())
}

export function daysBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number)
  const [ty, tm, td] = to.split("-").map(Number)
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000)
}

export function inWindow(window: MonthWindow, date: string): boolean {
  return date >= window.from && date < window.toExclusive
}
