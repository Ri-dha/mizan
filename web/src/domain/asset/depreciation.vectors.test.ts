import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/asset-depreciation.json"
import { depreciate, type DepreciationInput } from "./depreciation"

describe("asset-depreciation vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(depreciate(vector.input as DepreciationInput)).toEqual(vector.expected)
    })
  }
})
