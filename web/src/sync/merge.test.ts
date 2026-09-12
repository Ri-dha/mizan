import { describe, expect, it } from "vitest"

import { mergeFields } from "./merge"

const OLDER = "1700000000000:0001:device-a"
const NEWER = "1700000000001:0000:device-b"

describe("mergeFields", () => {
  it("takes the newer clock", () => {
    const outcome = mergeFields({ name: "Wallet" }, { name: OLDER }, { name: "Purse" }, { name: NEWER })
    expect(outcome.accepted).toEqual({ name: "Purse" })
    expect(outcome.clocks.name).toBe(NEWER)
    expect(outcome.conflicts).toHaveLength(0)
  })

  it("reports an older, different value as a conflict", () => {
    const outcome = mergeFields({ name: "Wallet" }, { name: NEWER }, { name: "Purse" }, { name: OLDER })
    expect(outcome.accepted).toEqual({})
    expect(outcome.conflicts).toEqual([
      { field: "name", clientValue: "Purse", serverValue: "Wallet", clientClock: OLDER, serverClock: NEWER },
    ])
  })

  it("ignores an older, identical value", () => {
    const outcome = mergeFields({ name: "Wallet" }, { name: NEWER }, { name: "Wallet" }, { name: OLDER })
    expect(outcome.accepted).toEqual({})
    expect(outcome.conflicts).toHaveLength(0)
  })

  it("merges fields independently", () => {
    const outcome = mergeFields(
      { name: "Wallet", balance: 100 }, { name: NEWER, balance: OLDER },
      { name: "Purse", balance: 250 }, { name: OLDER, balance: NEWER },
    )
    expect(outcome.accepted).toEqual({ balance: 250 })
    expect(outcome.conflicts.map((c) => c.field)).toEqual(["name"])
  })

  it("accepts anything when nothing is stored", () => {
    const outcome = mergeFields({}, {}, { name: "Wallet" }, { name: OLDER })
    expect(outcome.accepted).toEqual({ name: "Wallet" })
  })
})
