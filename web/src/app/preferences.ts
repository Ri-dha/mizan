import { useSyncExternalStore } from "react"

export type DigitStyle = "western" | "eastern"

export interface Preferences {
  digitStyle: DigitStyle
  showUsd: boolean
  quietMode: boolean
  notifications: Record<NotificationClass, boolean>
  /** Tour ids already shown on this device; deliberately not synced. */
  toursSeen: string[]
}

export type NotificationClass = "billDue" | "payDay" | "overspend" | "metalPrice" | "monthClose"
export const NOTIFICATION_CLASSES: NotificationClass[] = ["billDue", "payDay", "overspend", "metalPrice", "monthClose"]

const KEY = "mizan.preferences"
const DEFAULTS: Preferences = {
  digitStyle: "western",
  showUsd: false,
  quietMode: false,
  notifications: { billDue: true, payDay: true, overspend: true, metalPrice: false, monthClose: true },
  toursSeen: [],
}

let current: Preferences = load()
const listeners = new Set<() => void>()

function load(): Preferences {
  try {
    const stored = localStorage.getItem(KEY)
    return stored ? { ...DEFAULTS, ...JSON.parse(stored), notifications: { ...DEFAULTS.notifications, ...JSON.parse(stored).notifications } } : DEFAULTS
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
