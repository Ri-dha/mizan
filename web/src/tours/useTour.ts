import { useEffect } from "react"

import { hasSeen, startTour } from "./store"

const SETTLE_MS = 400

/** Starts a screen's tour once, after the screen has had a moment to render its data. */
export function useScreenTour(id: string, ready = true) {
  useEffect(() => {
    if (!ready || hasSeen(id)) return
    const timer = setTimeout(() => startTour(id), SETTLE_MS)
    return () => clearTimeout(timer)
  }, [id, ready])
}
