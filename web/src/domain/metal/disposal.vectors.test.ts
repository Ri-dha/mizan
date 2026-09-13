import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/lot-disposal.json"
import { InsufficientWeightError, planDisposal, type DisposalMethod } from "./disposal"

describe("lot-disposal vectors", () => {
  for (const vector of vectors) {
    it(vector.name, () => {
      const { lots, weightMg, method, specificLotIds, proceeds } = vector.input
      const run = () => planDisposal(lots, weightMg, method as DisposalMethod, specificLotIds, proceeds)
      if ("error" in vector.expected) expect(run).toThrow(InsufficientWeightError)
      else expect(run()).toEqual(vector.expected)
    })
  }
})
