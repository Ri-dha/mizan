export const BASIS_POINTS = 10_000

/**
 * Splits a whole amount by shares so the parts always sum to the whole (BR-02): each part
 * takes its floor, and the units left over go to the largest share.
 */
export function largestRemainderSplit(total: number, sharesBasisPoints: number[]): number[] {
  assertWhole(total)
  const sumShares = sharesBasisPoints.reduce((sum, share) => sum + share, 0)
  if (sumShares !== BASIS_POINTS) {
    throw new Error(`Shares must sum to 10,000 basis points, got ${sumShares}`)
  }

  const parts = sharesBasisPoints.map((share) => Math.floor((total * share) / BASIS_POINTS))
  const allocated = parts.reduce((sum, part) => sum + part, 0)
  const largest = sharesBasisPoints.reduce((best, share, i) => (share > sharesBasisPoints[best] ? i : best), 0)
  parts[largest] += total - allocated
  return parts
}

export function assertWhole(amount: number) {
  if (!Number.isSafeInteger(amount)) {
    throw new Error(`Money must be a safe whole number of minor units, got ${amount}`)
  }
}
