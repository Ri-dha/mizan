import { describe, expect, it } from "vitest"

import { microsToRate, rateToMicros, toBaseAmount } from "./income"

describe("exchange rates per minor unit", () => {
  it("stores a typed dinars-per-dollar rate per cent and shows it back per dollar", () => {
    expect(rateToMicros("1470", "USD")).toBe(14_700_000)
    expect(microsToRate(14_700_000, "USD")).toBe("1470")
    expect(rateToMicros("1470.5", "USD")).toBe(14_705_000)
    expect(rateToMicros("1", "IQD")).toBe(1_000_000)
  })

  it("converts ten dollars ninety-nine at 1,470 to whole dinars", () => {
    expect(toBaseAmount(1099, rateToMicros("1470", "USD"))).toBe(16_155)
  })
})
