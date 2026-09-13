export type DepreciationMethod = "NONE" | "STRAIGHT_LINE" | "DECLINING_BALANCE"

export interface DepreciationInput {
  baselineValue: number
  baselineDate: string
  salvageValue: number
  annualRateBasisPoints: number
  method: DepreciationMethod
  asOf: string
}

export interface DepreciationResult {
  value: number
  monthsElapsed: number
}

const BASIS_POINTS = 10_000n
const MONTHS_PER_YEAR = 12n

/** Whole months from one date to another, a month counting only once its day of month has passed. */
export function wholeMonthsBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number)
  const [ty, tm, td] = to.split("-").map(Number)
  let months = (ty - fy) * 12 + (tm - fm)
  if (td < fd) months -= 1
  return Math.max(0, months)
}

function monthlyStep(value: bigint, rateBasisPoints: number): bigint {
  const divisor = BASIS_POINTS * MONTHS_PER_YEAR
  return (value * BigInt(rateBasisPoints) + divisor / 2n) / divisor
}

/**
 * BR-13: an asset's current value runs from its latest manual valuation (or its purchase) and
 * declines month by month. Whole minor units; the monthly step is rounded half-up so both
 * implementations agree.
 */
export function depreciate(input: DepreciationInput): DepreciationResult {
  const months = wholeMonthsBetween(input.baselineDate, input.asOf)
  const baseline = BigInt(input.baselineValue)
  const floor = BigInt(Math.min(input.salvageValue, input.baselineValue))
  let value = baseline
  if (input.method === "STRAIGHT_LINE") {
    value = baseline - monthlyStep(baseline, input.annualRateBasisPoints) * BigInt(months)
  } else if (input.method === "DECLINING_BALANCE") {
    for (let month = 0; month < months && value > floor; month++) value -= monthlyStep(value, input.annualRateBasisPoints)
  }
  return { value: Number(value > floor ? value : floor), monthsElapsed: months }
}
