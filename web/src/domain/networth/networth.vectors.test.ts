import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/net-worth.json"
import { netWorth } from "./networth"

describe("net-worth vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      expect(netWorth(vector.input)).toEqual(vector.expected)
    })
  }
})
