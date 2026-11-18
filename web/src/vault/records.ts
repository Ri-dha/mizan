import { openField, readHeader, sealField } from "./crypto"
import { keyFor, sealTargetFor } from "./keyring"

/** The only values the server reads (ADR e2e-1): who may see the row, and when it was deleted. */
export const PLAIN_FIELDS: ReadonlySet<string> = new Set(["visibility", "deletedAt"])

/** Null when the key this row needs has not reached this device yet; the op then waits in the outbox. */
export async function sealFields(table: string, rowId: string, visibility: string | undefined, fields: Record<string, unknown>): Promise<Record<string, unknown> | null> {
  const target = sealTargetFor(table, visibility)
  if (!target) return null
  const sealed: Record<string, unknown> = {}
  for (const [field, value] of Object.entries(fields)) {
    sealed[field] = PLAIN_FIELDS.has(field) ? value : await sealField(target, table, rowId, field, value)
  }
  return sealed
}

export type Opened = { status: "open"; fields: Record<string, unknown> } | { status: "waiting" }

/** Opens every sealed field; a field this device holds no key for parks the whole record. */
export async function openFields(table: string, rowId: string, fields: Record<string, unknown>): Promise<Opened> {
  const opened: Record<string, unknown> = {}
  for (const [field, value] of Object.entries(fields)) {
    if (PLAIN_FIELDS.has(field)) {
      opened[field] = value
      continue
    }
    const header = readHeader(value)
    if (!header) throw new Error(`VAULT_NOT_SEALED:${table}.${field}`)
    const key = keyFor(header)
    if (!key) return { status: "waiting" }
    opened[field] = await openField(key, header, table, rowId, field)
  }
  return { status: "open", fields: opened }
}

/** Conflict values are ciphertext too; a value this device cannot open is shown as unknown. */
export async function openValue(table: string, rowId: string, field: string, value: unknown): Promise<unknown> {
  if (PLAIN_FIELDS.has(field)) return value
  const header = readHeader(value)
  if (!header) return value
  const key = keyFor(header)
  if (!key) return null
  try {
    return await openField(key, header, table, rowId, field)
  } catch {
    return null
  }
}
