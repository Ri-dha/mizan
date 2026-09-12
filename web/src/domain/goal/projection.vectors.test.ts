import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/goal-projection.json"
import { goalProjection } from "./projection"

describe("goal-projection vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      const { targetAmount, saved, monthlyContribution, targetDate, today } = vector.input
      expect(goalProjection(targetAmount, saved, monthlyContribution, targetDate, today)).toEqual(vector.expected)
    })
  }
})
