import type { MetalDisposal, MetalDisposalLot, MetalLot } from "@/db/schema"
import { valueOf } from "./valuation"

const BASIS_POINTS = 10_000n
const RATE_SCALE = 1_000_000

export interface SeriesPoint {
  day: string
  /** Cost basis of everything still held on that day, base units. */
  cost: number
  /** Worth at the dealer's selling price (ask) or the market price. */
  valueAsk: number | null
  /** What a dealer would pay for it (bid); null when no bid history exists for the day. */
  valueBid: number | null
}

export interface PriceByDay {
  ask: Record<string, number>
  bid: Record<string, number>
}

const toBase = (amount: number, fxRateMicros: number) => Math.round((amount * fxRateMicros) / RATE_SCALE)

/** Weight and remaining cost of a lot on a given day, after the sales made up to that day. */
function heldOn(lot: MetalLot, disposals: MetalDisposal[], disposalLots: MetalDisposalLot[], day: string) {
  if (lot.purchaseDate > day) return { weightMg: 0, cost: 0 }
  const soldByThen = new Set(disposals.filter((d) => d.soldOn <= day).map((d) => d.id))
  const sold = disposalLots.filter((dl) => dl.lotId === lot.id && soldByThen.has(dl.disposalId))
  const weightMg = lot.weightMg - sold.reduce((s, dl) => s + dl.weightMg, 0)
  const cost = toBase(lot.metalCost + lot.makingCharge + lot.fees, lot.fxRateMicros) - sold.reduce((s, dl) => s + dl.metalCost + dl.makingCharge + dl.fees, 0)
  return { weightMg: Math.max(0, weightMg), cost: Math.max(0, cost) }
}

function perGramAtPurity(perGram24kMicros: number, purityBasisPoints: number): number {
  return Number((BigInt(perGram24kMicros) * BigInt(purityBasisPoints) + BASIS_POINTS / 2n) / BASIS_POINTS)
}

/**
 * FR-MTL-08: the holding's cost against what it was worth, day by day, at the price you would
 * pay and at the price a dealer pays. Days without a price carry the last known one forward.
 */
export function holdingsSeries(lots: MetalLot[], disposals: MetalDisposal[], disposalLots: MetalDisposalLot[], days: string[], prices: PriceByDay): SeriesPoint[] {
  let lastAsk: number | null = null
  let lastBid: number | null = null
  return days.map((day) => {
    lastAsk = prices.ask[day] ?? lastAsk
    lastBid = prices.bid[day] ?? lastBid
    let cost = 0
    let valueAsk = 0
    let valueBid = 0
    for (const lot of lots) {
      const held = heldOn(lot, disposals, disposalLots, day)
      if (held.weightMg <= 0) continue
      cost += held.cost
      if (lastAsk !== null) valueAsk += valueOf(perGramAtPurity(lastAsk, lot.purityBasisPoints), held.weightMg)
      if (lastBid !== null) valueBid += valueOf(perGramAtPurity(lastBid, lot.purityBasisPoints), held.weightMg)
    }
    return { day, cost, valueAsk: lastAsk === null ? null : valueAsk, valueBid: lastBid === null ? null : valueBid }
  })
}

export interface Verdict {
  proceeds: number
  cost: number
  gain: number
  gainPercent: number | null
  /** The dealer's per-gram price for pure metal at which this holding breaks even. */
  breakEvenPerGram24kMicros: number | null
}

/** Sell or hold, today: what the dealer would pay against what it cost (making charges included, BR-12). */
export function sellVerdict(lots: { lot: MetalLot; remainingMg: number; costBasisBase: number }[], bidPerGram24kMicros: number | null): Verdict {
  const cost = lots.reduce((s, l) => s + l.costBasisBase, 0)
  const pureMg = lots.reduce((s, l) => s + Math.round((l.remainingMg * l.lot.purityBasisPoints) / Number(BASIS_POINTS)), 0)
  const proceeds = bidPerGram24kMicros === null ? 0 : lots.reduce((s, l) => s + valueOf(perGramAtPurity(bidPerGram24kMicros, l.lot.purityBasisPoints), l.remainingMg), 0)
  const gain = proceeds - cost
  return {
    proceeds, cost, gain,
    gainPercent: cost > 0 ? Math.round((gain / cost) * 1000) / 10 : null,
    breakEvenPerGram24kMicros: pureMg > 0 ? Math.round((cost * 1_000_000 * 1000) / pureMg) : null,
  }
}

/** Every day from `from` to `to` inclusive. */
export function dayRange(from: string, to: string): string[] {
  const days: string[] = []
  const cursor = new Date(`${from}T00:00:00Z`)
  const end = new Date(`${to}T00:00:00Z`)
  while (cursor <= end) {
    days.push(cursor.toISOString().slice(0, 10))
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return days
}
