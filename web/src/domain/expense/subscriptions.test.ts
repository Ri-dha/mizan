import { describe, expect, it } from "vitest"

import type { RecurringExpense } from "@/db/schema"
import { isSubscription, subscriptionSummary, yearlyCost } from "./subscriptions"

const base = { activeFrom: "2026-01-01", activeTo: null, fxRateMicros: 1_000_000, currency: "IQD" }
const spotify = { ...base, name: "Spotify", category: "Subscriptions", frequency: "MONTHLY", amount: 1099, currency: "USD", fxRateMicros: 14_700_000 } as RecurringExpense
const youtube = { ...base, name: "YouTube", category: "اشتراكات", frequency: "ANNUAL", amount: 120_000 } as RecurringExpense
const rent = { ...base, name: "Rent", category: "Housing", frequency: "MONTHLY", amount: 600_000 } as RecurringExpense
const ended = { ...spotify, name: "Netflix", activeTo: "2026-06-30" } as RecurringExpense

describe("subscriptions", () => {
  it("recognises the category in both languages and ignores other bills", () => {
    expect(isSubscription(spotify)).toBe(true)
    expect(isSubscription(youtube)).toBe(true)
    expect(isSubscription(rent)).toBe(false)
  })

  it("compares monthly and yearly plans on a yearly footing", () => {
    expect(yearlyCost(spotify)).toBe(13_188)
    expect(yearlyCost(youtube)).toBe(120_000)
    expect(yearlyCost({ ...base, frequency: "CUSTOM", intervalDays: 30, amount: 3000 } as RecurringExpense)).toBe(36_525)
  })

  it("totals active subscriptions in base currency and dollars at today's rate", () => {
    const summary = subscriptionSummary([spotify, youtube, rent, ended], "2026-09-15", 1_470_000_000)
    expect(summary.lines.map((l) => l.expense.name)).toEqual(["Spotify", "YouTube"])
    expect(summary.lines[0].monthlyBase).toBe(16_155)
    expect(summary.monthlyBase).toBe(16_155 + 10_000)
    expect(summary.yearlyBase).toBe(193_864 + 120_000)
    expect(summary.monthlyUsd).toBe(1779)
    expect(subscriptionSummary([spotify], "2026-09-15", 0).monthlyUsd).toBeNull()
  })
})
