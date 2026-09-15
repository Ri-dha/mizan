import { describe, expect, it } from "vitest"

import type { MetalDisposal, MetalDisposalLot, MetalLot } from "@/db/schema"
import { dayRange, holdingsSeries, sellVerdict } from "./series"

const lot = { id: "l1", metal: "GOLD", purityBasisPoints: 8750, weightMg: 5000, purchaseDate: "2026-09-02", metalCost: 520_000, makingCharge: 30_000, fees: 0, fxRateMicros: 1_000_000 } as MetalLot

describe("holdings series", () => {
  it("values what is held each day at ask and bid and carries the last price forward", () => {
    const prices = { ask: { "2026-09-01": 120_000_000_000, "2026-09-03": 125_000_000_000 }, bid: { "2026-09-01": 119_000_000_000 } }
    const series = holdingsSeries([lot], [], [], dayRange("2026-09-01", "2026-09-03"), prices)
    expect(series[0]).toEqual({ day: "2026-09-01", cost: 0, valueAsk: 0, valueBid: 0 })
    expect(series[1]).toEqual({ day: "2026-09-02", cost: 550_000, valueAsk: 525_000, valueBid: 520_625 })
    expect(series[2].valueAsk).toBe(546_875)
  })

  it("drops sold weight from the day of the sale", () => {
    const disposal = { id: "d1", soldOn: "2026-09-03" } as MetalDisposal
    const disposalLot = { disposalId: "d1", lotId: "l1", weightMg: 2500, metalCost: 260_000, makingCharge: 15_000, fees: 0 } as MetalDisposalLot
    const series = holdingsSeries([lot], [disposal], [disposalLot], ["2026-09-02", "2026-09-03"], { ask: { "2026-09-02": 120_000_000_000 }, bid: {} })
    expect(series[1]).toMatchObject({ cost: 275_000, valueAsk: 262_500, valueBid: null })
  })

  it("says whether selling to the dealer today gains or loses, and the break-even price", () => {
    const verdict = sellVerdict([{ lot, remainingMg: 5000, costBasisBase: 550_000 }], 216_424_630_000)
    expect(verdict.proceeds).toBe(946_858)
    expect(verdict.gain).toBe(396_858)
    expect(verdict.gainPercent).toBe(72.2)
    expect(verdict.breakEvenPerGram24kMicros).toBe(125_714_285_714)
  })
})
