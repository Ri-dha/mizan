import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/metal-valuation.json"
import { metalValuation } from "./valuation"

describe("metal-valuation vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(metalValuation(vector.input)).toEqual(vector.expected)
    })
  }
})
