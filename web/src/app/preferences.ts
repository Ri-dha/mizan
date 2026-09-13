import { useSyncExternalStore } from "react"

export type DigitStyle = "western" | "eastern"

export interface Preferences {
  digitStyle: DigitStyle
  showUsd: boolean
  /** Tour ids already shown on this device; deliberately not synced. */
  toursSeen: string[]
  /** FR-AST-04: months after which an asset without a fresh valuation is flagged on Home. */
  valuationReminderMonths: number
  /** FR-SET-04: Hijri date alongside the Gregorian one. */
  showHijri: boolean
}


const KEY = "mizan.preferences"
const DEFAULTS: Preferences = {
  digitStyle: "western",
  showUsd: false,
  toursSeen: [],
  valuationReminderMonths: 6,
  showHijri: false,
}

let current: Preferences = load()
const listeners = new Set<() => void>()

function load(): Preferences {
  try {
    const stored = localStorage.getItem(KEY)
    return stored ? { ...DEFAULTS, ...JSON.parse(stored) } : DEFAULTS
  } catch {
    return DEFAULTS
  }
}

export function preferences(): Preferences {
  return current
}

export function setPreferences(patch: Partial<Preferences>) {
  current = { ...current, ...patch }
  try {
    localStorage.setItem(KEY, JSON.stringify(current))
  } catch {
    // Per-device convenience only; losing it costs nothing.
  }
  listeners.forEach((l) => l())
}

export function usePreferences(): Preferences {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => current,
  )
}
