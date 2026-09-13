import { beforeEach, describe, expect, it } from "vitest"

import { createIncomeSource, liveAmountHistory, occurrencesFor, recordAmountChange } from "./income"
import { db } from "./schema"

const SALARY = {
  name: "Salary", amount: 1_500_000, currency: "IQD", fxRateMicros: 1_000_000, frequency: "MONTHLY" as const,
  payDay: 25, anchorDate: null, activeFrom: "2026-01-01", activeTo: null, note: null, visibility: "SHARED" as const,
}

describe("income amount history", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it("a new source starts its history at its first amount", async () => {
    await createIncomeSource(SALARY)
    const history = await liveAmountHistory()
    expect(history).toHaveLength(1)
    expect(history[0]).toMatchObject({ effectiveFrom: "2026-01-01", amount: 1_500_000 })
  })

  it("a raise changes later months but not earlier ones", async () => {
    const id = await createIncomeSource(SALARY)
    const source = (await db.incomeSources.get(id))!
    await recordAmountChange(source, 1_750_000, "2026-10-01", "Promotion")

    const sources = await db.incomeSources.toArray()
    const history = await liveAmountHistory()
    expect(sources[0].amount).toBe(1_750_000)
    expect(occurrencesFor(sources, [], "2026-09", 1, history)[0].amount).toBe(1_500_000)
    expect(occurrencesFor(sources, [], "2026-10", 1, history)[0].amount).toBe(1_750_000)
  })
})

describe("sources created before the history existed", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it("get their original amount recorded when the first change is made", async () => {
    const id = await createIncomeSource(SALARY)
    await db.incomeSourceAmounts.clear()
    const source = (await db.incomeSources.get(id))!
    await recordAmountChange(source, 1_750_000, "2026-10-01", null)

    const history = await liveAmountHistory()
    expect(history.map((h) => [h.effectiveFrom, h.amount])).toEqual([["2026-01-01", 1_500_000], ["2026-10-01", 1_750_000]])
    expect(occurrencesFor(await db.incomeSources.toArray(), [], "2026-09", 1, history)[0].amount).toBe(1_500_000)
  })
})
