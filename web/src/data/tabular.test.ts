import { unzipSync } from "fflate"
import { describe, expect, it } from "vitest"

import { toCsv, toXlsx, type Table } from "./tabular"

const TABLE: Table = { headers: ["Date", "Payee", "Amount"], rows: [["2026-09-13", "Ali, \"the\" grocer", 25000], ["2026-09-14", "بقالة", 1500]] }

describe("tabular exports", () => {
  it("writes RFC 4180 CSV with a BOM and quoted fields", () => {
    const csv = toCsv(TABLE)
    expect(csv.startsWith("﻿")).toBe(true)
    expect(csv).toContain('"Ali, ""the"" grocer"')
    expect(csv.trim().split("\r\n")).toHaveLength(3)
  })

  it("writes an .xlsx package with one sheet and inline strings", () => {
    const files = unzipSync(toXlsx(TABLE, "Transactions", true))
    expect(Object.keys(files).sort()).toEqual(["[Content_Types].xml", "_rels/.rels", "xl/_rels/workbook.xml.rels", "xl/workbook.xml", "xl/worksheets/sheet1.xml"])
    const sheet = new TextDecoder().decode(files["xl/worksheets/sheet1.xml"])
    expect(sheet).toContain('rightToLeft="1"')
    expect(sheet).toContain("<t>بقالة</t>")
    expect(sheet).toContain('<c r="C2"><v>25000</v></c>')
  })
})
