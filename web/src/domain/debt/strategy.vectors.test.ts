import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/debt-strategy.json"
import { compareStrategies } from "./strategy"

describe("debt-strategy vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(compareStrategies(vector.input.debts, vector.input.extraMonthly)).toEqual(vector.expected)
    })
  }
})
