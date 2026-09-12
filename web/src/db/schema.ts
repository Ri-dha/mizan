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

export type ExpenseFrequency = "MONTHLY" | "QUARTERLY" | "ANNUAL" | "CUSTOM"

export interface RecurringExpense extends Syncable {
  visibility: Visibility
  name: string
  bucketId: string | null
  amount: number
  isEstimate: boolean
  currency: string
  fxRateMicros: number
  frequency: ExpenseFrequency
  dueDay: number | null
  anchorDate: string | null
  intervalDays: number | null
  activeFrom: string
  activeTo: string | null
  category: string | null
  note: string | null
  sortOrder: number
}

export interface ExpenseOccurrence extends Syncable {
  visibility: Visibility
  recurringExpenseId: string
  dueDate: string
  monthKey: string
  expectedAmount: number
  actualAmount: number
  paidOn: string
  transactionId: string | null
}

export type TransactionType = "EXPENSE" | "INCOME" | "TRANSFER"
export type CounterpartyType = "BUCKET" | "GOAL" | "DEBT" | "CASH_ACCOUNT"

export interface LedgerTransaction extends Syncable {
  visibility: Visibility
  type: TransactionType
  occurredOn: string
  monthKey: string
  amount: number
  currency: string
  fxRateMicros: number
  baseAmount: number
  bucketId: string | null
  category: string | null
  payee: string | null
  note: string | null
  counterpartyType: CounterpartyType | null
  counterpartyId: string | null
  attachmentId: string | null
}

export interface Attachment extends Syncable {
  visibility: Visibility
  ownerType: string
  ownerRecordId: string
  mimeType: string
  byteSize: number
  iv: string
  uploadedAt: string | null
}

/** Encrypted bytes kept on this device; `uploaded` flips once object storage has them. */
export interface LocalBlob {
  id: string
  bytes: ArrayBuffer
  uploaded: boolean
}

export type DebtDirection = "OWING" | "OWED"

export interface Debt extends Syncable {
  visibility: Visibility
  name: string
  counterparty: string | null
  direction: DebtDirection
  principal: number
  currency: string
  fxRateMicros: number
  annualRateBasisPoints: number
  termMonths: number | null
  monthlyPayment: number
  bucketId: string | null
  startDate: string
  status: "ACTIVE" | "SETTLED"
  note: string | null
}

export interface DebtPayment extends Syncable {
  visibility: Visibility
  debtId: string
  paidOn: string
  monthKey: string
  amount: number
  interestComponent: number
  principalComponent: number
  transactionId: string | null
  note: string | null
}

export interface Goal extends Syncable {
  visibility: Visibility
  name: string
  targetAmount: number
  currency: string
  targetDate: string | null
  monthlyContribution: number | null
  bucketId: string | null
  backingAssetType: string | null
  backingAssetId: string | null
  status: "ACTIVE" | "COMPLETED" | "ARCHIVED"
  completedOn: string | null
  note: string | null
  sortOrder: number
}

export interface GoalDeposit extends Syncable {
  visibility: Visibility
  goalId: string
  depositedOn: string
  monthKey: string
  amount: number
  direction: "DEPOSIT" | "WITHDRAWAL"
  transactionId: string | null
  note: string | null
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
  recurring_expense: "recurringExpenses",
  expense_occurrence: "expenseOccurrences",
  ledger_transaction: "transactions",
  attachment: "attachments",
  debt: "debts",
  debt_payment: "debtPayments",
  goal: "goals",
  goal_deposit: "goalDeposits",
} as const

export type SyncTableName = keyof typeof SYNC_TABLES

export class MizanDatabase extends Dexie {
  cashAccounts!: EntityTable<CashAccount, "id">
  incomeSources!: EntityTable<IncomeSource, "id">
  incomeReceipts!: EntityTable<IncomeReceipt, "id">
  plans!: EntityTable<Plan, "id">
  buckets!: EntityTable<Bucket, "id">
  recurringExpenses!: EntityTable<RecurringExpense, "id">
  expenseOccurrences!: EntityTable<ExpenseOccurrence, "id">
  transactions!: EntityTable<LedgerTransaction, "id">
  attachments!: EntityTable<Attachment, "id">
  blobs!: EntityTable<LocalBlob, "id">
  debts!: EntityTable<Debt, "id">
  debtPayments!: EntityTable<DebtPayment, "id">
  goals!: EntityTable<Goal, "id">
  goalDeposits!: EntityTable<GoalDeposit, "id">
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
    this.version(3).stores({
      recurringExpenses: "id, deletedAt, sortOrder",
      expenseOccurrences: "id, deletedAt, monthKey, recurringExpenseId, [recurringExpenseId+dueDate]",
      transactions: "id, deletedAt, monthKey, occurredOn, bucketId, type, [monthKey+bucketId]",
      attachments: "id, deletedAt, ownerRecordId",
      blobs: "id, uploaded",
      debts: "id, deletedAt, status",
      debtPayments: "id, deletedAt, debtId, monthKey",
      goals: "id, deletedAt, status, sortOrder",
      goalDeposits: "id, deletedAt, goalId, monthKey",
    })
  }
}

export const db = new MizanDatabase()

export function localTableFor(table: SyncTableName) {
  return db.table<Syncable, string>(SYNC_TABLES[table])
}
