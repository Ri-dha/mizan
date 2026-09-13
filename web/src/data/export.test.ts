import { beforeEach, describe, expect, it } from "vitest"

import { createCashAccount } from "@/db/cashAccounts"
import { db } from "@/db/schema"
import { applyImport, buildExport, EXPORT_FORMAT, EXPORT_VERSION, ImportError, validateImport } from "./export"

const SESSION = { userId: "u", householdId: "h", householdName: "Home", displayName: "R", contact: "", locale: "en", verified: true, role: "OWNER", baseCurrency: "IQD", monthStartDay: 1, deletionRequestedAt: null }

describe("export and import", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it("round-trips the household through a file", async () => {
    await createCashAccount({ name: "Wallet", kind: "WALLET", institution: null, balance: 5000, currency: "IQD", visibility: "SHARED" })
    const file = await buildExport(SESSION)
    expect(file.format).toBe(EXPORT_FORMAT)
    expect(file.tables.cash_account).toHaveLength(1)
    expect(file.tables.cash_account[0]).not.toHaveProperty("clocks")

    await db.delete()
    await db.open()
    const preview = validateImport(JSON.parse(JSON.stringify(file)))
    expect(preview.counts.cash_account).toBe(1)
    expect(await applyImport(preview)).toBe(1)
    expect((await db.cashAccounts.toArray())[0]).toMatchObject({ name: "Wallet", balance: 5000 })
    expect(await db.outbox.count()).toBe(1)
  })

  it("refuses files that are not Mizan exports, with a specific reason", () => {
    expect(() => validateImport("nope")).toThrow(ImportError)
    expect(() => validateImport({ format: "other" })).toThrow(/not a Mizan export/)
    expect(() => validateImport({ format: EXPORT_FORMAT, version: 99, tables: {} })).toThrow(/version/)
    expect(() => validateImport({ format: EXPORT_FORMAT, version: EXPORT_VERSION, tables: { cash_account: [{ name: "x" }] } })).toThrow(/no id/)
    expect(() => validateImport({ format: EXPORT_FORMAT, version: EXPORT_VERSION, tables: {} })).toThrow(/no importable rows/)
  })

  it("skips server-written tables instead of pushing them", () => {
    const preview = validateImport({ format: EXPORT_FORMAT, version: EXPORT_VERSION, tables: { cash_account: [{ id: "a", name: "W" }], net_worth_snapshot: [{ id: "s" }] } })
    expect(preview.counts).toEqual({ cash_account: 1 })
    expect(preview.skipped).toEqual(["net_worth_snapshot"])
  })
})
