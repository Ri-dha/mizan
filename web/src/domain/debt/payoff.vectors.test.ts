import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/debt-payoff.json"
import { debtPayoff } from "./payoff"

describe("debt-payoff vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      const { balance, annualRateBasisPoints, monthlyPayment } = vector.input
      expect(debtPayoff(balance, annualRateBasisPoints, monthlyPayment)).toEqual(vector.expected)
    })
  }
})
