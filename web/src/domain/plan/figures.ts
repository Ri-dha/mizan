import { BASIS_POINTS, largestRemainderSplit } from "@/domain/money/split"

export interface BucketShare {
  id: string
  shareBasisPoints: number
}

export interface MonthFiguresInput {
  plannedIncome: number
  receivedIncome: number
  buckets: BucketShare[]
  committed: Record<string, number>
  spent: Record<string, number>
  transfersIn: Record<string, number>
  transfersOut: Record<string, number>
}

export interface BucketFigures {
  id: string
  plannedAllocated: number
  allocated: number
  committed: number
  spent: number
  transfersIn: number
  transfersOut: number
  free: number
}

export interface MonthFiguresResult {
  buckets: BucketFigures[]
  totalShareBasisPoints: number
  unallocatedPlanned: number
  unallocatedReceived: number
}

/**
 * The per-bucket view of a month (FR-PLN-05): what each bucket was allocated from planned and
 * from received income, what it has committed, spent and transferred, and what is free.
 * Transfers are never spending (BR-04) but they do move money out of a bucket.
 */
export function monthFigures(input: MonthFiguresInput): MonthFiguresResult {
  const shares = input.buckets.map((b) => b.shareBasisPoints)
  const planned = allocate(input.plannedIncome, shares)
  const received = allocate(input.receivedIncome, shares)

  const buckets = input.buckets.map((bucket, i) => {
    const committed = input.committed[bucket.id] ?? 0
    const spent = input.spent[bucket.id] ?? 0
    const transfersIn = input.transfersIn[bucket.id] ?? 0
    const transfersOut = input.transfersOut[bucket.id] ?? 0
    const allocated = received[i]
    return {
      id: bucket.id, plannedAllocated: planned[i], allocated, committed, spent, transfersIn, transfersOut,
      free: allocated + transfersIn - committed - spent - transfersOut,
    }
  })

  const sum = (values: number[]) => values.reduce((total, value) => total + value, 0)
  return {
    buckets,
    totalShareBasisPoints: sum(shares),
    unallocatedPlanned: input.plannedIncome - sum(planned),
    unallocatedReceived: input.receivedIncome - sum(received),
  }
}

/**
 * A plan that sums to 100% splits exactly (BR-02). One that does not gets each share's floor,
 * and the difference surfaces as the unallocated amount the warning shows (FR-PLN-04).
 */
export function allocate(total: number, sharesBasisPoints: number[]): number[] {
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
