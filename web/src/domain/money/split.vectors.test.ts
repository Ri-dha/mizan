import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/money-split.json"
import { largestRemainderSplit } from "./split"

/** The same cases the API runs; both must agree to the dinar. */
describe("money-split vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(largestRemainderSplit(vector.input.total, vector.input.sharesBasisPoints)).toEqual(vector.expected)
    })
  }
})
