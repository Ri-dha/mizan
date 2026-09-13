import { strToU8, zipSync } from "fflate"

import type { Asset, AssetValuation, LedgerTransaction, MetalLot, MetalDisposalLot, Bucket } from "@/db/schema"
import { currentValue } from "@/db/assets"
import { lotStates } from "@/db/metals"

export type Cell = string | number | null
export interface Table {
  headers: string[]
  rows: Cell[][]
}

const MG_PER_GRAM = 1000

/** Column labels come from the caller so exports read in the household's language. */
export function transactionsTable(transactions: LedgerTransaction[], buckets: Bucket[], labels: string[]): Table {
  const bucketName = (id: string | null) => buckets.find((b) => b.id === id)?.name ?? ""
  return {
    headers: labels,
    rows: transactions.map((t) => [t.occurredOn, t.type, t.amount, t.currency, t.baseAmount, bucketName(t.bucketId), t.category, t.payee, t.note]),
  }
}

export function lotsTable(lots: MetalLot[], disposalLots: MetalDisposalLot[], labels: string[]): Table {
  return {
    headers: labels,
    rows: lotStates(lots, disposalLots).map((state) => [
      state.lot.purchaseDate, state.lot.metal, state.lot.purityLabel, state.lot.form, state.lot.weightMg / MG_PER_GRAM,
      state.remainingMg / MG_PER_GRAM, state.lot.metalCost, state.lot.makingCharge, state.lot.fees, state.lot.currency, state.lot.dealer, state.lot.note,
    ]),
  }
}

export function assetsTable(assets: Asset[], valuations: AssetValuation[], today: string, labels: string[]): Table {
  return {
    headers: labels,
    rows: assets.map((a) => {
      const value = currentValue(a, valuations, today)
      return [a.name, a.type, a.status, a.purchaseDate, a.purchasePrice, a.currency, value.value, value.baselineDate, a.liquidity, a.soldOn, a.salePrice]
    }),
  }
}

/** RFC 4180 with a byte-order mark so Excel opens Arabic text correctly. */
export function toCsv(table: Table): string {
  const escape = (cell: Cell) => {
    if (cell === null || cell === undefined) return ""
    const text = String(cell)
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, "\"\"")}"` : text
  }
  return "﻿" + [table.headers, ...table.rows].map((row) => row.map(escape).join(",")).join("\r\n") + "\r\n"
}

const xml = (text: string) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")

function columnName(index: number): string {
  let name = ""
  let n = index + 1
  while (n > 0) {
    const rem = (n - 1) % 26
    name = String.fromCharCode(65 + rem) + name
    n = Math.floor((n - 1) / 26)
  }
  return name
}

/** A minimal .xlsx (one sheet, inline strings) written by hand so no spreadsheet library is needed. */
export function toXlsx(table: Table, sheetName: string, rtl: boolean): Uint8Array<ArrayBuffer> {
  const cell = (value: Cell, r: number, c: number) => {
    const ref = `${columnName(c)}${r}`
    if (value === null || value === undefined || value === "") return ""
    if (typeof value === "number") return `<c r="${ref}"><v>${value}</v></c>`
    return `<c r="${ref}" t="inlineStr"><is><t>${xml(String(value))}</t></is></c>`
  }
  const rows = [table.headers, ...table.rows].map((row, r) => `<row r="${r + 1}">${row.map((v, c) => cell(v, r + 1, c)).join("")}</row>`).join("")
  const sheet = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"${rtl ? " rightToLeft=\"1\"" : ""}/></sheetViews><sheetData>${rows}</sheetData></worksheet>`
  const workbook = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xml(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>`
  const workbookRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`
  const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`
  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>`
  const zipped = zipSync({
    "[Content_Types].xml": strToU8(contentTypes),
    "_rels/.rels": strToU8(rels),
    "xl/workbook.xml": strToU8(workbook),
    "xl/_rels/workbook.xml.rels": strToU8(workbookRels),
    "xl/worksheets/sheet1.xml": strToU8(sheet),
  })
  const copy = new Uint8Array(new ArrayBuffer(zipped.byteLength))
  copy.set(zipped)
  return copy
}

export function downloadBlob(name: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
