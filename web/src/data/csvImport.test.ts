import { describe, expect, it } from "vitest"

import type { LedgerTransaction } from "@/db/schema"
import { guessMapping, guessPositiveIs, mapRows, parseCsv, parseDate } from "./csvImport"

const FILE = '﻿Date,Description,Amount,Category\r\n2026-09-05,"Zain, top-up",-10000,Phone\r\n13/09/2026,Salary,1500000,\r\nbad,Nothing,x,\r\n'

describe("csv import", () => {
  it("parses quoted fields and a byte-order mark", () => {
    const table = parseCsv(FILE)
    expect(table.headers).toEqual(["Date", "Description", "Amount", "Category"])
    expect(table.rows[0][1]).toBe("Zain, top-up")
    expect(table.rows).toHaveLength(3)
  })

  it("guesses a mapping from the headers", () => {
    expect(guessMapping(["Date", "Description", "Amount", "Category"])).toEqual(["date", "payee", "amount", "category"])
  })

  it("reads several date styles", () => {
    expect(parseDate("2026-09-05")).toBe("2026-09-05")
    expect(parseDate("13/09/2026")).toBe("2026-09-13")
    expect(parseDate("09/13/2026")).toBe("2026-09-13")
    expect(parseDate("nope")).toBeNull()
  })

  it("maps rows, flags errors and detects duplicates", () => {
    const existing = [{ occurredOn: "2026-09-13", type: "INCOME", amount: 1500000, payee: "Salary" }] as LedgerTransaction[]
    const table = parseCsv(FILE)
    expect(guessPositiveIs(table, ["date", "payee", "amount", "category"])).toBe("INCOME")
    const rows = mapRows(table, ["date", "payee", "amount", "category"], existing, (v) => Math.round(v), "INCOME")
    expect(rows[0]).toMatchObject({ occurredOn: "2026-09-05", amount: 10000, type: "EXPENSE", payee: "Zain, top-up", category: "Phone", duplicate: false, error: null })
    expect(rows[1]).toMatchObject({ occurredOn: "2026-09-13", amount: 1500000, type: "INCOME", duplicate: true })
    expect(rows[2].error).toBe("date")
  })

  it("a plain list of positive amounts is read as expenses", () => {
    const table = parseCsv("date,amount\n2026-09-01,5000\n")
    expect(guessPositiveIs(table, ["date", "amount"])).toBe("EXPENSE")
    expect(mapRows(table, ["date", "amount"], [], (v) => v, "EXPENSE")[0].type).toBe("EXPENSE")
  })
})
