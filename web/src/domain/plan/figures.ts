import { BASIS_POINTS, largestRemainderSplit } from "@/domain/money/split"

/** A bucket with a fixed amount is funded first and takes no share of the remainder (FR-PLN-07). */
export interface BucketShare {
  id: string
  shareBasisPoints: number
  fixedAmount: number | null
}

export interface MonthFiguresInput {
  plannedIncome: number
  receivedIncome: number
  buckets: BucketShare[]
  carriedIn: Record<string, number>
  committed: Record<string, number>
  spent: Record<string, number>
  transfersIn: Record<string, number>
  transfersOut: Record<string, number>
}

export interface BucketFigures {
  id: string
  plannedAllocated: number
  allocated: number
  carriedIn: number
  committed: number
  spent: number
  transfersIn: number
  transfersOut: number
  free: number
}

export interface MonthFiguresResult {
  buckets: BucketFigures[]
  totalShareBasisPoints: number
  fixedTotal: number
  unallocatedPlanned: number
  unallocatedReceived: number
}

/**
 * The per-bucket view of a month (FR-PLN-05): what each bucket was allocated from planned and
 * from received income, what it carried in from last month, what it has committed, spent and
 * transferred, and what is free. Transfers are never spending (BR-04) but they do move money
 * out of a bucket.
 */
export function monthFigures(input: MonthFiguresInput): MonthFiguresResult {
  const planned = allocate(input.plannedIncome, input.buckets)
  const received = allocate(input.receivedIncome, input.buckets)

  const buckets = input.buckets.map((bucket, i) => {
    const carriedIn = input.carriedIn[bucket.id] ?? 0
    const committed = input.committed[bucket.id] ?? 0
    const spent = input.spent[bucket.id] ?? 0
    const transfersIn = input.transfersIn[bucket.id] ?? 0
    const transfersOut = input.transfersOut[bucket.id] ?? 0
    const allocated = received[i]
    return {
      id: bucket.id, plannedAllocated: planned[i], allocated, carriedIn, committed, spent, transfersIn, transfersOut,
      free: allocated + carriedIn + transfersIn - committed - spent - transfersOut,
    }
  })

  const sum = (values: number[]) => values.reduce((total, value) => total + value, 0)
  return {
    buckets,
    totalShareBasisPoints: sum(input.buckets.filter((b) => b.fixedAmount === null).map((b) => b.shareBasisPoints)),
    fixedTotal: sum(input.buckets.map((b) => b.fixedAmount ?? 0)),
    unallocatedPlanned: input.plannedIncome - sum(planned),
    unallocatedReceived: input.receivedIncome - sum(received),
  }
}

/**
 * Fixed amounts are funded first, in bucket order, until the income runs out; the remainder
 * splits by percentage. Shares that sum to 100% split exactly (BR-02); shares that do not get
 * each share's floor, and the difference surfaces as the unallocated amount (FR-PLN-04).
 */
export function allocate(total: number, buckets: BucketShare[]): number[] {
  let remaining = total
  const fixed: (number | null)[] = []
  const shares: number[] = []
  for (const bucket of buckets) {
    if (bucket.fixedAmount !== null) {
      const funded = Math.max(0, Math.min(bucket.fixedAmount, remaining))
      remaining -= funded
      fixed.push(funded)
    } else {
      fixed.push(null)
      shares.push(bucket.shareBasisPoints)
    }
  }
  const split = splitShares(remaining, shares)
  let shareIndex = 0
  return fixed.map((amount) => (amount !== null ? amount : split[shareIndex++]))
}

function splitShares(total: number, sharesBasisPoints: number[]): number[] {
  const sum = sharesBasisPoints.reduce((a, b) => a + b, 0)
  if (sum === BASIS_POINTS) return largestRemainderSplit(total, sharesBasisPoints)
  return sharesBasisPoints.map((share) => Math.floor((total * share) / BASIS_POINTS))
}

/** Percentages are entered with one decimal place; shares are stored in basis points. */
export function percentToBasisPoints(percent: number): number {
  return Math.round(percent * 100)
}

export function basisPointsToPercent(basisPoints: number): number {
  return basisPoints / 100
}

/** Scales shares to exactly 100% (FR-PLN-04's rebalance), keeping their proportions. */
export function rebalance(sharesBasisPoints: number[]): number[] {
  if (sharesBasisPoints.length === 0) return []
  const sum = sharesBasisPoints.reduce((a, b) => a + b, 0)
  if (sum === 0) {
    const each = Math.floor(BASIS_POINTS / sharesBasisPoints.length)
    const parts = sharesBasisPoints.map(() => each)
    parts[0] += BASIS_POINTS - each * parts.length
    return parts
  }
  const parts = sharesBasisPoints.map((share) => Math.floor((BASIS_POINTS * share) / sum))
  const largest = sharesBasisPoints.reduce((best, share, i) => (share > sharesBasisPoints[best] ? i : best), 0)
  parts[largest] += BASIS_POINTS - parts.reduce((a, b) => a + b, 0)
  return parts
}
