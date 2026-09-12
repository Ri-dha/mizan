import { useSyncExternalStore } from "react"

import { monthKeyFor, shiftKey, todayIso } from "@/domain/calendar/month"

let selected: string | null = null
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

export function currentMonthKey(startDay: number): string {
  return monthKeyFor(todayIso(), startDay)
}

/** The month the plan and income screens are looking at; defaults to today's household month. */
export function useSelectedMonth(startDay: number): [string, (key: string) => void] {
  const key = useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => selected ?? currentMonthKey(startDay),
  )
  return [key, (next) => { selected = next; emit() }]
}

export function shiftMonth(key: string, months: number): string {
  return shiftKey(key, months)
}

export function formatMonthKey(key: string, locale: string): string {
  const [year, month] = key.split("-").map(Number)
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-IQ" : "en-GB", { month: "long", year: "numeric" })
    .format(new Date(Date.UTC(year, month - 1, 1)))
}
