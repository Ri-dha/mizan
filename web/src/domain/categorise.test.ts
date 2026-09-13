import { describe, expect, it } from "vitest"

import type { LedgerTransaction } from "@/db/schema"
import { normalise, suggestCategory } from "./categorise"

const history = [
  { type: "EXPENSE", payee: "Zain", category: "Phone", bucketId: "b-essentials" },
  { type: "EXPENSE", payee: "Zain", category: "Phone", bucketId: "b-essentials" },
  { type: "EXPENSE", payee: "Zain", category: "Gifts", bucketId: "b-fun" },
  { type: "EXPENSE", payee: "مطعم أبو علي", category: "Eating out", bucketId: "b-fun" },
] as LedgerTransaction[]

describe("auto-categorisation", () => {
  it("normalises Arabic and Latin text alike", () => {
    expect(normalise("  Zain, TOP-UP ")).toBe("zain top up")
    expect(normalise("مَطعَمْ الأمير")).toBe("مطعم الامير")
  })

  it("prefers what the household called this payee before", () => {
    expect(suggestCategory("zain", "", history)).toEqual({ category: "Phone", bucketId: "b-essentials", confidence: "high", reason: "history" })
  })

  it("matches a payee by one of its words", () => {
    expect(suggestCategory("مطعم النخيل", "", history)).toMatchObject({ category: "Eating out", confidence: "medium" })
  })

  it("falls back to bilingual keywords", () => {
    expect(suggestCategory("Baghdad Pharmacy", "", [])).toMatchObject({ category: "Pharmacy", reason: "keyword" })
    expect(suggestCategory("", "دفعة المولدة", [])).toMatchObject({ category: "Generator" })
    expect(suggestCategory("Ali", "", [])).toBeNull()
  })
})
