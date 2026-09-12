import Dexie, { type EntityTable } from "dexie"

export type Visibility = "SHARED" | "PRIVATE"
export type CashAccountKind = "WALLET" | "BANK" | "CASH_AT_HOME" | "OTHER"

/** Every syncable row carries this envelope; `clocks` holds one HLC stamp per field. */
export interface Syncable {
  id: string
  ownerId: string | null
  clocks: Record<string, string>
  deletedAt: string | null
}

export interface CashAccount extends Syncable {
  visibility: Visibility
  name: string
  kind: CashAccountKind
  institution: string | null
  balance: number
  currency: string
  sortOrder: number
}

export interface OutboxOp {
  opId: string
  table: SyncTableName
  rowId: string
  fields: Record<string, unknown>
  clocks: Record<string, string>
  createdAt: number
}

export interface LocalConflict {
  id: string
  table: SyncTableName
  rowId: string
  field: string
  clientValue: unknown
  serverValue: unknown
  detectedAt: string
  dismissed: boolean
}

export interface MetaEntry {
  key: string
  value: unknown
}

export const SYNC_TABLES = {
  cash_account: "cashAccounts",
} as const

export type SyncTableName = keyof typeof SYNC_TABLES

export class MizanDatabase extends Dexie {
  cashAccounts!: EntityTable<CashAccount, "id">
  outbox!: EntityTable<OutboxOp, "opId">
  conflicts!: EntityTable<LocalConflict, "id">
  meta!: EntityTable<MetaEntry, "key">

  constructor(name = "mizan") {
    super(name)
    this.version(1).stores({
      cashAccounts: "id, deletedAt, sortOrder, currency",
      outbox: "opId, createdAt, rowId",
      conflicts: "id, detectedAt, dismissed",
      meta: "key",
    })
  }
}

export const db = new MizanDatabase()

export function localTableFor(table: SyncTableName) {
  return db.table<Syncable, string>(SYNC_TABLES[table])
}
