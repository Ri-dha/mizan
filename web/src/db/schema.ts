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

export type Frequency = "MONTHLY" | "BIWEEKLY" | "WEEKLY" | "ONE_OFF"

export interface IncomeSource extends Syncable {
  visibility: Visibility
  name: string
  amount: number
  currency: string
  fxRateMicros: number
  frequency: Frequency
  payDay: number | null
  anchorDate: string | null
  activeFrom: string
  activeTo: string | null
  note: string | null
  sortOrder: number
}

export interface IncomeReceipt extends Syncable {
  visibility: Visibility
  incomeSourceId: string | null
  monthKey: string
  receivedOn: string
  amount: number
  currency: string
  fxRateMicros: number
  baseAmount: number
  note: string | null
}

export interface Plan extends Syncable {
  visibility: Visibility
  effectiveFrom: string
  effectiveTo: string | null
}

export interface Bucket extends Syncable {
  visibility: Visibility
  planId: string
  name: string
  colour: string
  shareBasisPoints: number
  fixedAmount: number | null
  carryOver: boolean
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
  income_source: "incomeSources",
  income_receipt: "incomeReceipts",
  plan: "plans",
  bucket: "buckets",
} as const

export type SyncTableName = keyof typeof SYNC_TABLES

export class MizanDatabase extends Dexie {
  cashAccounts!: EntityTable<CashAccount, "id">
  incomeSources!: EntityTable<IncomeSource, "id">
  incomeReceipts!: EntityTable<IncomeReceipt, "id">
  plans!: EntityTable<Plan, "id">
  buckets!: EntityTable<Bucket, "id">
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
    this.version(2).stores({
      incomeSources: "id, deletedAt, sortOrder",
      incomeReceipts: "id, deletedAt, monthKey, incomeSourceId",
      plans: "id, deletedAt, effectiveFrom",
      buckets: "id, deletedAt, planId, sortOrder",
    })
  }
}

export const db = new MizanDatabase()

export function localTableFor(table: SyncTableName) {
  return db.table<Syncable, string>(SYNC_TABLES[table])
}
