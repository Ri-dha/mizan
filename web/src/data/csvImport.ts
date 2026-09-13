import type { LedgerTransaction, TransactionType } from "@/db/schema"

export type CsvField = "date" | "amount" | "payee" | "category" | "note" | "type" | "ignore"

/** Phase 4: a remembered mapping for a statement format, recalled by its header row. */
export interface CsvProfile {
  name: string
  signature: string
  mapping: CsvField[]
  positiveIs: TransactionType
  bucketId: string | null
}

export const headerSignature = (headers: string[]) => headers.map((h) => h.trim().toLowerCase()).join("|")

export interface CsvTable {
  headers: string[]
  rows: string[][]
}

export interface ImportedRow {
  index: number
  occurredOn: string
  amount: number
  type: TransactionType
  payee: string | null
  category: string | null
  note: string | null
  duplicate: boolean
  error: string | null
}

/** RFC 4180: quoted fields, doubled quotes, CR LF or LF, and a leading byte-order mark. */
export function parseCsv(text: string): CsvTable {
  const rows: string[][] = []
  let row: string[] = []
  let field = ""
  let quoted = false
  const source = text.startsWith("﻿") ? text.slice(1) : text
  for (let i = 0; i < source.length; i++) {
    const ch = source[i]
    if (quoted) {
      if (ch === '"') {
        if (source[i + 1] === '"') {
          field += '"'
          i++
        } else quoted = false
      } else field += ch
    } else if (ch === '"') quoted = true
    else if (ch === ",") {
      row.push(field)
      field = ""
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && source[i + 1] === "\n") i++
      row.push(field)
      rows.push(row)
      row = []
      field = ""
    } else field += ch
  }
  if (field !== "" || row.length > 0) {
    row.push(field)
    rows.push(row)
  }
  const nonEmpty = rows.filter((r) => r.some((c) => c.trim() !== ""))
  const [headers = [], ...body] = nonEmpty
  return { headers: headers.map((h) => h.trim()), rows: body }
}

const HEADER_HINTS: Record<CsvField, RegExp> = {
  date: /date|تاريخ|day/i,
  amount: /amount|مبلغ|total|sum|value/i,
  payee: /payee|merchant|vendor|جهة|to|description|desc/i,
  category: /categor|فئة|type of|tag/i,
  note: /note|memo|ملاحظ|comment/i,
  type: /^type$|نوع|direction|debit|credit/i,
  ignore: /$^/,
}

/** A first guess at which column is which; the user confirms or changes it. */
export function guessMapping(headers: string[]): CsvField[] {
  const taken = new Set<CsvField>()
  return headers.map((header) => {
    for (const field of ["date", "amount", "type", "category", "note", "payee"] as CsvField[]) {
      if (!taken.has(field) && HEADER_HINTS[field].test(header)) {
        taken.add(field)
        return field
      }
    }
    return "ignore"
  })
}

/** ISO first, then day/month/year and day-month-year, then month/day/year as a last resort. */
export function parseDate(raw: string): string | null {
  const text = raw.trim()
  let m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(text)
  if (m) return iso(Number(m[1]), Number(m[2]), Number(m[3]))
  m = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(text)
  if (m) {
    const [, a, b, y] = m.map(Number)
    if (a > 12) return iso(y, b, a)
    if (b > 12) return iso(y, a, b)
    return iso(y, b, a)
  }
  return null
}

function iso(y: number, m: number, d: number): string | null {
  if (m < 1 || m > 12 || d < 1 || d > 31) return null
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`
}

export function parseAmount(raw: string): number | null {
  const cleaned = raw.replace(/[^\d.,-]/g, "").replace(/,/g, "")
  if (cleaned === "" || cleaned === "-") return null
  const value = Number(cleaned)
  return Number.isFinite(value) ? value : null
}

function typeOf(raw: string | undefined, amount: number, positiveIs: TransactionType): TransactionType {
  const text = (raw ?? "").trim().toLowerCase()
  if (/income|credit|دخل|in$/.test(text)) return "INCOME"
  if (/transfer|تحويل/.test(text)) return "TRANSFER"
  if (/expense|debit|مصروف|out$/.test(text)) return "EXPENSE"
  if (amount < 0) return positiveIs === "EXPENSE" ? "INCOME" : "EXPENSE"
  return positiveIs
}

/** Statements usually show money out as negative; a plain list of expenses has no sign at all. */
export function guessPositiveIs(table: CsvTable, mapping: CsvField[]): TransactionType {
  const column = mapping.indexOf("amount")
  if (column < 0) return "EXPENSE"
  return table.rows.some((cells) => (parseAmount(cells[column] ?? "") ?? 0) < 0) ? "INCOME" : "EXPENSE"
}

/** Rows in the file's order; a duplicate is one that already exists with the same date, amount and payee. */
export function mapRows(table: CsvTable, mapping: CsvField[], existing: LedgerTransaction[], toMinor: (value: number) => number, positiveIs: TransactionType = "EXPENSE"): ImportedRow[] {
  const column = (field: CsvField) => mapping.indexOf(field)
  const seen = new Set(existing.map((t) => `${t.occurredOn}|${t.type === "INCOME" ? t.amount : -t.amount}|${(t.payee ?? "").toLowerCase()}`))
  return table.rows.map((cells, index) => {
    const dateCell = cells[column("date")] ?? ""
    const amountCell = cells[column("amount")] ?? ""
    const occurredOn = parseDate(dateCell)
    const value = parseAmount(amountCell)
    const payee = column("payee") >= 0 ? cells[column("payee")]?.trim() || null : null
    const category = column("category") >= 0 ? cells[column("category")]?.trim() || null : null
    const note = column("note") >= 0 ? cells[column("note")]?.trim() || null : null
    const type = typeOf(column("type") >= 0 ? cells[column("type")] : undefined, value ?? 0, positiveIs)
    const amount = value === null ? 0 : toMinor(Math.abs(value))
    const error = occurredOn === null ? "date" : value === null || amount === 0 ? "amount" : null
    const duplicate = error === null && seen.has(`${occurredOn}|${type === "INCOME" ? amount : -amount}|${(payee ?? "").toLowerCase()}`)
    return { index, occurredOn: occurredOn ?? "", amount, type, payee, category, note, duplicate, error }
  })
}
