export const MICROGRAMS_PER_TROY_OUNCE = 31_103_500n
const BASIS_POINTS = 10_000n
const MICROS_PER_GRAM_MILLIGRAM = 1_000_000_000n
const MICRO = 1_000_000n

export interface ValuationInput {
  spotUsdPerOzMicros: number
  usdIqdMicros: number
  purityBasisPoints: number
  premiumBasisPoints: number
  premiumFixedPerGramMicros: number
  weightMg: number
  /** A user-entered IQD price per gram of pure metal that replaces spot × rate (FR-MTL-09). */
  overridePerGram24kMicros?: number | null
}

export interface ValuationResult {
  perGram24kMicros: number
  perGramMicros: number
  value: number
}

function halfUp(numerator: bigint, divisor: bigint): bigint {
  return (numerator + divisor / 2n) / divisor
}

/**
 * BR-08 in exact integer arithmetic: spot USD per troy ounce → IQD per gram of pure metal →
 * the lot's purity → the local premium → the lot's weight. BigInt because spot × rate in
 * micros passes 2^53; the four rounding points match the server exactly.
 */
export function metalValuation(input: ValuationInput): ValuationResult {
  const perGram24k = input.overridePerGram24kMicros != null
    ? BigInt(input.overridePerGram24kMicros)
    : halfUp(BigInt(input.spotUsdPerOzMicros) * BigInt(input.usdIqdMicros), MICROGRAMS_PER_TROY_OUNCE)
  const perGramPurity = halfUp(perGram24k * BigInt(input.purityBasisPoints), BASIS_POINTS)
  const perGram = halfUp(perGramPurity * (BASIS_POINTS + BigInt(input.premiumBasisPoints)), BASIS_POINTS) + BigInt(input.premiumFixedPerGramMicros)
  const value = halfUp(perGram * BigInt(input.weightMg), MICROS_PER_GRAM_MILLIGRAM)
  return { perGram24kMicros: Number(perGram24k), perGramMicros: Number(perGram), value: Number(value) }
}

/** Whole units of the base currency for a price in micros per gram and a weight in milligrams. */
export function valueOf(perGramMicros: number, weightMg: number): number {
  return Number(halfUp(BigInt(perGramMicros) * BigInt(weightMg), MICROS_PER_GRAM_MILLIGRAM))
}

export function microsToWhole(micros: number): number {
  return Number(halfUp(BigInt(micros), MICRO))
}

export const MILLIGRAMS_PER_UNIT: Record<string, number> = {
  GRAM: 1_000,
  MITHQAL: 5_000,
  TOLA: 11_664,
  TROY_OUNCE: 31_104,
  KILOGRAM: 1_000_000,
}

export function toMilligrams(quantity: number, unit: string): number {
  return Math.round(quantity * MILLIGRAMS_PER_UNIT[unit])
}

export const PURITIES: Record<string, { label: string; basisPoints: number; metal: "GOLD" | "SILVER" }> = {
  "24k": { label: "24k", basisPoints: 9990, metal: "GOLD" },
  "22k": { label: "22k", basisPoints: 9160, metal: "GOLD" },
  "21k": { label: "21k", basisPoints: 8750, metal: "GOLD" },
  "18k": { label: "18k", basisPoints: 7500, metal: "GOLD" },
  "999": { label: "999", basisPoints: 9990, metal: "SILVER" },
  "925": { label: "925", basisPoints: 9250, metal: "SILVER" },
}
