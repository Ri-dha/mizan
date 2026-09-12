import { describe, expect, it } from "vitest"

import { formatHlc, isAfter, observe, parseHlc, tick } from "./hlc"

describe("hybrid logical clock", () => {
  it("formats stamps that sort as strings in causal order", () => {
    const a = formatHlc({ wall: 1700000000000, counter: 1, node: "a" })
    const b = formatHlc({ wall: 1700000000000, counter: 2, node: "a" })
    const c = formatHlc({ wall: 1700000000001, counter: 0, node: "a" })
    expect(a < b && b < c).toBe(true)
    expect(a).toMatch(/^\d{13}:[0-9a-f]{4}:a$/)
  })

  it("moves forward with wall time and counts within one millisecond", () => {
    const start = { wall: 1000, counter: 0, node: "n" }
    expect(tick(start, 2000)).toEqual({ wall: 2000, counter: 0, node: "n" })
    expect(tick(start, 1000)).toEqual({ wall: 1000, counter: 1, node: "n" })
    expect(tick(start, 900)).toEqual({ wall: 1000, counter: 1, node: "n" })
  })

  it("never issues a stamp behind one it has observed", () => {
    const local = { wall: 1000, counter: 3, node: "n" }
    const remote = formatHlc({ wall: 5000, counter: 7, node: "m" })
    const after = observe(local, remote, 1000)
    expect(after.wall).toBe(5000)
    expect(after.counter).toBe(8)
    expect(isAfter(formatHlc(after), remote)).toBe(true)
  })

  it("round-trips through parse", () => {
    const state = { wall: 1700000000123, counter: 255, node: "device-x" }
    expect(parseHlc(formatHlc(state))).toEqual(state)
  })
})
