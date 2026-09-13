import fc from "fast-check"
import { describe, it } from "vitest"

import { planDisposal, type DisposalMethod, type PlannerLot } from "./metal/disposal"
import { largestRemainderSplit } from "./money/split"
import { netWorth } from "./networth/networth"
import { monthFigures } from "./plan/figures"

const MAX_AMOUNT = 1_000_000_000_000

/** Shares in basis points that sum to exactly 10,000: random cut points on the line. */
const exactShares = fc.array(fc.integer({ min: 0, max: 10_000 }), { maxLength: 8 }).map((cuts) => {
  const sorted = [...cuts].sort((a, b) => a - b)
  const parts: number[] = []
  let previous = 0
  for (const cut of sorted) {
    parts.push(cut - previous)
    previous = cut
  }
  parts.push(10_000 - previous)
  return parts
})

const amountMap = fc.dictionary(fc.integer({ min: 0, max: 7 }).map(String), fc.integer({ min: 0, max: MAX_AMOUNT / 1000 }))

const lot: fc.Arbitrary<PlannerLot> = fc.record({
  id: fc.uuid(),
  purchaseDate: fc.integer({ min: 0, max: 3000 }).map((d) => new Date(Date.UTC(2020, 0, 1 + d)).toISOString().slice(0, 10)),
  weightMg: fc.integer({ min: 1, max: 1_000_000 }),
  metalCost: fc.integer({ min: 0, max: 100_000_000 }),
  makingCharge: fc.integer({ min: 0, max: 10_000_000 }),
  fees: fc.integer({ min: 0, max: 1_000_000 }),
}).map((l) => ({ ...l, remainingMg: l.weightMg }))

/** NFR-06 / OBJ-1: every aggregate equals the sum of its parts, for any input, not just the vectors. */
describe("reconciliation properties", () => {
  it("split parts always sum to the whole", () => {
    fc.assert(fc.property(fc.integer({ min: 0, max: MAX_AMOUNT }), exactShares, (total, shares) => {
      const parts = largestRemainderSplit(total, shares)
      return parts.length === shares.length && parts.reduce((a, b) => a + b, 0) === total && parts.every((p) => p >= 0)
    }))
  })

  it("allocated plus unallocated is the income and free is the identity", () => {
    fc.assert(fc.property(
      fc.integer({ min: 0, max: MAX_AMOUNT }), fc.integer({ min: 0, max: MAX_AMOUNT }),
      fc.array(fc.integer({ min: 0, max: 10_000 }), { maxLength: 8 }), amountMap, amountMap, amountMap, amountMap,
      (planned, received, shares, committed, spent, fixed, carried) => {
        const buckets = shares.map((s, i) => ({ id: `b${i}`, shareBasisPoints: s, fixedAmount: fixed[i] ?? null }))
        const byId = (m: Record<string, number>) => Object.fromEntries(Object.entries(m).map(([k, v]) => [`b${k}`, v]))
        const result = monthFigures({ plannedIncome: planned, receivedIncome: received, buckets, carriedIn: byId(carried), committed: byId(committed), spent: byId(spent), transfersIn: {}, transfersOut: {} })
        const allocated = result.buckets.reduce((s, b) => s + b.allocated, 0)
        const plannedAllocated = result.buckets.reduce((s, b) => s + b.plannedAllocated, 0)
        return allocated + result.unallocatedReceived === received
          && plannedAllocated + result.unallocatedPlanned === planned
          && result.buckets.every((b, i) => b.allocated >= 0 && (buckets[i].fixedAmount === null || b.allocated <= buckets[i].fixedAmount))
          && result.buckets.every((b) => b.free === b.allocated + b.carriedIn + b.transfersIn - b.committed - b.spent - b.transfersOut)
      },
    ))
  })

  it("a sale draws exactly the weight sold and carries exactly the cost basis removed", () => {
    fc.assert(fc.property(
      fc.array(lot, { minLength: 1, maxLength: 6 }), fc.integer({ min: 1, max: 100 }), fc.constantFrom<DisposalMethod>("FIFO", "SPECIFIC", "WEIGHTED_AVERAGE"),
      (lots, fraction, method) => {
        const open = lots.reduce((s, l) => s + l.remainingMg, 0)
        const weight = Math.max(1, Math.floor((open * fraction) / 100))
        const plan = planDisposal(lots, weight, method, lots.map((l) => l.id), 0)
        const drawn = plan.allocations.reduce((s, a) => s + a.weightMg, 0)
        const within = plan.allocations.every((a) => { const l = lots.find((x) => x.id === a.lotId)!; return a.weightMg >= 1 && a.weightMg <= l.remainingMg })
        return drawn === weight
          && plan.costMetal + plan.costMaking + plan.costFees === plan.costBasis
          && plan.realisedGainExcludingMaking - plan.realisedGain === plan.costMaking
          && within
      },
    ))
  })

  it("net worth is assets minus liabilities and the shares cover the assets", () => {
    fc.assert(fc.property(
      fc.integer({ min: 0, max: MAX_AMOUNT }), fc.integer({ min: 0, max: MAX_AMOUNT }), fc.integer({ min: 0, max: MAX_AMOUNT }),
      fc.integer({ min: 0, max: MAX_AMOUNT }), fc.integer({ min: 0, max: MAX_AMOUNT }), fc.integer({ min: 0, max: MAX_AMOUNT }),
      (cash, metals, receivables, vehicles, property, liabilities) => {
        const illiquid = Math.min(vehicles, property)
        const result = netWorth({ cash, metals, receivables, vehicles, property, otherAssets: 0, illiquid, liabilities })
        const shares = result.composition.reduce((s, c) => s + c.amount, 0)
        const percent = result.composition.reduce((s, c) => s + c.percent, 0)
        return result.totalAssets === cash + metals + receivables + vehicles + property
          && result.liquidAssets + result.illiquidAssets === result.totalAssets
          && result.netWorth === result.totalAssets - result.totalLiabilities
          && shares === result.totalAssets
          && (result.totalAssets === 0 || (percent >= 99.7 && percent <= 100.3))
      },
    ))
  })
})
