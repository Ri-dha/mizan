import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/planned-income.json"
import { plannedIncome, type PlannedSource } from "./planned"

describe("planned-income vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      const window = { key: "vector", ...vector.input.window }
      expect(plannedIncome(vector.input.sources as PlannedSource[], window)).toEqual(vector.expected)
    })
  }
})
