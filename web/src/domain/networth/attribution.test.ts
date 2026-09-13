import { describe, expect, it } from "vitest"
import fc from "fast-check"

import { attributeChange, type Reading } from "./attribution"

describe("net worth change attribution", () => {
  it("splits a month into saving, price movement, debt repayment and purchases", () => {
    const before: Reading = { cash: 1_000_000, metals: 500_000, receivables: 0, otherAssets: 0, liabilities: 4_000_000 }
    const after: Reading = { cash: 1_300_000, metals: 1_100_000, receivables: 0, otherAssets: 0, liabilities: 3_800_000 }
    const result = attributeChange(before, after, { purchases: 520_000, disposals: 0 })
    expect(result).toEqual({ total: 1_100_000, saving: 300_000, priceMovement: 80_000, debtRepayment: 200_000, newPurchases: 520_000 })
  })

  it("always sums to the total change", () => {
    const amount = fc.integer({ min: 0, max: 1_000_000_000 })
    const reading = fc.record({ cash: amount, metals: amount, receivables: amount, otherAssets: amount, liabilities: amount })
    fc.assert(fc.property(reading, reading, amount, amount, (before, after, purchases, disposals) => {
      const r = attributeChange(before, after, { purchases, disposals })
      return r.saving + r.priceMovement + r.debtRepayment + r.newPurchases === r.total
    }))
  })
})
