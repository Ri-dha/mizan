import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/month-figures.json"
import { monthFigures } from "./figures"

describe("month-figures vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(monthFigures(vector.input)).toEqual(vector.expected)
    })
  }
})
