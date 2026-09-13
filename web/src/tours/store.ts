import { useSyncExternalStore } from "react"

import { preferences, setPreferences } from "@/app/preferences"
import { TOURS, type TourDefinition } from "./steps"

let active: TourDefinition | null = null
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((l) => l())
}

export function startTour(id: string) {
  const tour = TOURS[id]
  if (!tour) return
  active = tour
  emit()
}

export function endTour() {
  if (active) markSeen(active.id)
  active = null
  emit()
}

export function useActiveTour(): TourDefinition | null {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => active,
  )
}

export function hasSeen(id: string): boolean {
  return preferences().toursSeen.includes(id)
}

export function markSeen(id: string) {
  const seen = preferences().toursSeen
  if (!seen.includes(id)) setPreferences({ toursSeen: [...seen, id] })
}

export function forgetAllTours() {
  setPreferences({ toursSeen: [] })
}
