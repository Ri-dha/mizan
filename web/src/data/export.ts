import { db, SYNC_TABLES, type SyncTableName } from "@/db/schema"
import { writeFields } from "@/db/write"
import type { LocalSession } from "@/api/auth"

export const EXPORT_FORMAT = "mizan-export"
export const EXPORT_VERSION = 1

/** Tables the server writes; an import never pushes them (they come back through sync). */
const SERVER_ONLY: ReadonlySet<SyncTableName> = new Set(["net_worth_snapshot", "month_close"])
const ENVELOPE = new Set(["clocks"])

export interface ExportFile {
  format: typeof EXPORT_FORMAT
  version: number
  exportedAt: string
  household: { id: string; name: string; baseCurrency: string; monthStartDay: number }
  tables: Record<string, Record<string, unknown>[]>
}

/** FR-DAT-01: the whole household as the device holds it, including the recycle bin. */
export async function buildExport(session: LocalSession): Promise<ExportFile> {
  const tables: Record<string, Record<string, unknown>[]> = {}
  for (const [wire, local] of Object.entries(SYNC_TABLES)) {
    tables[wire] = (await db.table(local).toArray()).map((row: Record<string, unknown>) => {
      const copy: Record<string, unknown> = {}
      for (const [key, value] of Object.entries(row)) if (!ENVELOPE.has(key)) copy[key] = value
      return copy
    })
  }
  return {
    format: EXPORT_FORMAT,
    version: EXPORT_VERSION,
    exportedAt: new Date().toISOString(),
    household: { id: session.householdId, name: session.householdName, baseCurrency: session.baseCurrency, monthStartDay: session.monthStartDay },
    tables,
  }
}

export class ImportError extends Error {
  readonly code: string

  constructor(code: string, detail: string) {
    super(detail)
    this.code = code
  }
}

export interface ImportPreview {
  file: ExportFile
  counts: Record<string, number>
  skipped: string[]
}

/** FR-DAT-03: refuses anything that is not a complete Mizan export before a single row is written. */
export function validateImport(raw: unknown): ImportPreview {
  if (typeof raw !== "object" || raw === null) throw new ImportError("NOT_JSON_OBJECT", "The file is not a JSON object")
  const file = raw as Partial<ExportFile>
  if (file.format !== EXPORT_FORMAT) throw new ImportError("WRONG_FORMAT", "The file is not a Mizan export")
  if (file.version !== EXPORT_VERSION) throw new ImportError("WRONG_VERSION", `Export version ${String(file.version)} is not supported`)
  if (typeof file.tables !== "object" || file.tables === null) throw new ImportError("NO_TABLES", "The export has no tables")

  const counts: Record<string, number> = {}
  const skipped: string[] = []
  for (const [table, rows] of Object.entries(file.tables)) {
    if (!(table in SYNC_TABLES)) {
      skipped.push(table)
      continue
    }
    if (!Array.isArray(rows)) throw new ImportError("TABLE_NOT_ARRAY", `Table ${table} is not a list`)
    rows.forEach((row, i) => {
      if (typeof row !== "object" || row === null || typeof (row as { id?: unknown }).id !== "string") {
        throw new ImportError("ROW_WITHOUT_ID", `Row ${i} of ${table} has no id`)
      }
    })
    if (SERVER_ONLY.has(table as SyncTableName)) skipped.push(table)
    else counts[table] = rows.length
  }
  if (Object.keys(counts).length === 0) throw new ImportError("EMPTY", "The export contains no importable rows")
  return { file: file as ExportFile, counts, skipped }
}

const EXCLUDED_ON_IMPORT = new Set(["id", "ownerId", "clocks"])

/** Writes every row through the normal write path, so the import syncs like any local edit. */
export async function applyImport(preview: ImportPreview): Promise<number> {
  let written = 0
  await db.transaction("rw", db.tables, async () => {
    for (const table of Object.keys(preview.counts) as SyncTableName[]) {
      for (const row of preview.file.tables[table]) {
        const fields: Record<string, unknown> = {}
        for (const [key, value] of Object.entries(row)) if (!EXCLUDED_ON_IMPORT.has(key)) fields[key] = value
        await writeFields(table, row.id as string, fields as never)
        written += 1
      }
    }
  })
  return written
}

export function downloadJson(name: string, data: unknown) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" })
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
