import { useEffect, useState } from "react"

import { api, unwrap } from "@/api/client"
import { readMeta, writeMeta } from "@/db/meta"
import type { Quote } from "./store"

export type HistoryInstrument = Quote["instrument"]
export type History = Partial<Record<HistoryInstrument, Record<string, number>>>

const KEY = "market.history"
const DAYS = 365

async function fetchHistory(instrument: HistoryInstrument): Promise<Record<string, number>> {
  const to = new Date().toISOString().slice(0, 10)
  const from = new Date(Date.now() - DAYS * 86_400_000).toISOString().slice(0, 10)
  const points = await unwrap(api.GET("/api/v1/market/history", { params: { query: { instrument, from, to } } }))
  return Object.fromEntries(points.map((p) => [p.day ?? "", p.priceMicros ?? 0]))
}

/** Daily closes for charts (FR-MTL-08): served from the local copy, refreshed when online. */
export function useHistory(instruments: HistoryInstrument[]): History {
  const [history, setHistory] = useState<History>({})
  const key = instruments.join(",")
  useEffect(() => {
    let cancelled = false
    void (async () => {
      const cached = (await readMeta<History>(KEY)) ?? {}
      if (!cancelled) setHistory(cached)
      if (!navigator.onLine) return
      const fresh: History = { ...cached }
      for (const instrument of key.split(",") as HistoryInstrument[]) {
        try {
          fresh[instrument] = await fetchHistory(instrument)
        } catch {
          // keep the cached series for this instrument
        }
      }
      await writeMeta(KEY, fresh)
      if (!cancelled) setHistory(fresh)
    })()
    return () => { cancelled = true }
  }, [key])
  return history
}
