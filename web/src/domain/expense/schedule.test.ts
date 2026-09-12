import { describe, expect, it } from "vitest"

import { dueDates } from "./schedule"

const september = { key: "2026-09", from: "2026-09-01", toExclusive: "2026-10-01" }

describe("bill schedules", () => {
  it("monthly bills fall on their due day, clamped to short months", () => {
    expect(dueDates({ frequency: "MONTHLY", dueDay: 31, anchorDate: null, intervalDays: null, activeFrom: "2026-01-01", activeTo: null }, september))
      .toEqual(["2026-09-30"])
  })

  it("quarterly bills step three months from their anchor", () => {
    const schedule = { frequency: "QUARTERLY" as const, dueDay: null, anchorDate: "2026-03-15", intervalDays: null, activeFrom: "2026-03-15", activeTo: null }
    expect(dueDates(schedule, september)).toEqual(["2026-09-15"])
    expect(dueDates(schedule, { key: "2026-08", from: "2026-08-01", toExclusive: "2026-09-01" })).toEqual([])
  })

  it("annual bills recur on the anchor's month and day", () => {
    expect(dueDates({ frequency: "ANNUAL", dueDay: null, anchorDate: "2025-09-20", intervalDays: null, activeFrom: "2025-09-20", activeTo: null }, september))
      .toEqual(["2026-09-20"])
  })

  it("custom intervals count in days and stop when the bill ends", () => {
    expect(dueDates({ frequency: "CUSTOM", dueDay: null, anchorDate: "2026-08-30", intervalDays: 10, activeFrom: "2026-08-30", activeTo: "2026-09-20" }, september))
      .toEqual(["2026-09-09", "2026-09-19"])
  })
})
