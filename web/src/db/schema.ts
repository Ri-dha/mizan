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

/** One entry of a source's amount history: what it paid from a given date. */
export interface IncomeSourceAmount extends Syncable {
  visibility: Visibility
  incomeSourceId: string
  effectiveFrom: string
  amount: number
  currency: string
  fxRateMicros: number
  note: string | null
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
  /** FR-NTF-02: days before the due date to remind; null uses the household default. */
  reminderDays: number | null
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
  /** FR-TRX-06: the parts of one purchase share a group id and sum to its total. */
  splitGroupId: string | null
  /** FR-AST-07: a running cost of an asset (fuel, repairs, insurance). */
  assetId: string | null
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

export type Metal = "GOLD" | "SILVER"
export type MetalForm = "COIN" | "BAR" | "JEWELLERY" | "SCRAP"
export type WeightUnit = "GRAM" | "MITHQAL" | "TOLA" | "TROY_OUNCE" | "KILOGRAM"
export type DisposalMethod = "FIFO" | "SPECIFIC" | "WEIGHTED_AVERAGE"
export type MarketInstrument = "XAU" | "XAG" | "USDIQD"
export type RateKind = "OFFICIAL" | "PARALLEL"
export type ValuationBasis = "MARKET" | "BUYBACK"
export type PriceSource = "WORLD" | "LOCAL"

export interface MetalLot extends Syncable {
  visibility: Visibility
  metal: Metal
  purityLabel: string
  purityBasisPoints: number
  weightMg: number
  weightUnitEntered: WeightUnit
  quantityEntered: string
  purchaseDate: string
  metalCost: number
  makingCharge: number
  fees: number
  currency: string
  fxRateMicros: number
  form: MetalForm
  dealer: string | null
  location: string | null
  serial: string | null
  heldFor: string | null
  note: string | null
}

export interface MetalDisposal extends Syncable {
  visibility: Visibility
  metal: Metal
  soldOn: string
  weightMg: number
  proceeds: number
  fees: number
  currency: string
  fxRateMicros: number
  method: DisposalMethod
  buyer: string | null
  note: string | null
}

export interface MetalDisposalLot extends Syncable {
  visibility: Visibility
  disposalId: string
  lotId: string
  weightMg: number
  metalCost: number
  makingCharge: number
  fees: number
}

export interface MarketOverride extends Syncable {
  visibility: Visibility
  instrument: MarketInstrument
  priceMicros: number
  effectiveFrom: string
  note: string | null
}

export interface MarketSetting extends Syncable {
  visibility: Visibility
  rateKind: RateKind
  goldPremiumBasisPoints: number
  silverPremiumBasisPoints: number
  goldMethod: DisposalMethod
  silverMethod: DisposalMethod
  valuationBasis: ValuationBasis
  goldBuybackBasisPoints: number
  silverBuybackBasisPoints: number
  priceSource: PriceSource
}

export interface CashAdjustment extends Syncable {
  visibility: Visibility
  cashAccountId: string
  adjustedOn: string
  previousBalance: number
  newBalance: number
  note: string | null
}

export interface PrivacySetting extends Syncable {
  visibility: Visibility
  transactions: Visibility
  accounts: Visibility
  metals: Visibility
  debts: Visibility
  goals: Visibility
  assets: Visibility
}

export interface NotificationSetting extends Syncable {
  visibility: Visibility
  billDue: boolean
  billLeadDays: number
  payDay: boolean
  overspend: boolean
  overspendThresholdBp: number
  monthClose: boolean
  metalPrice: boolean
  metalMoveBp: number
  quietMode: boolean
}

export interface BucketMove extends Syncable {
  visibility: Visibility
  fromBucketId: string
  toBucketId: string
  monthKey: string
  movedOn: string
  amount: number
  reason: string | null
  transactionId: string | null
}

export type AssetType = "VEHICLE" | "PROPERTY" | "ELECTRONICS" | "EQUIPMENT" | "FURNITURE" | "LIVESTOCK" | "OTHER"
export type Liquidity = "LIQUID" | "ILLIQUID"

export interface Asset extends Syncable {
  visibility: Visibility
  type: AssetType
  name: string
  purchaseDate: string | null
  purchasePrice: number
  currency: string
  fxRateMicros: number
  liquidity: Liquidity
  depreciationMethod: "NONE" | "STRAIGHT_LINE" | "DECLINING_BALANCE"
  annualRateBasisPoints: number
  salvageValue: number
  attributes: Record<string, string | number>
  status: "HELD" | "SOLD"
  soldOn: string | null
  salePrice: number | null
  note: string | null
  sortOrder: number
}

export interface AssetValuation extends Syncable {
  visibility: Visibility
  assetId: string
  valuedOn: string
  value: number
  source: "MANUAL" | "ESTIMATED"
  note: string | null
}

export interface NetWorthSnapshot extends Syncable {
  visibility: Visibility
  monthKey: string
  takenAt: string
  totalAssets: number
  totalLiabilities: number
  netWorth: number
  liquidAssets: number | null
  illiquidAssets: number | null
  composition: { assetClass: string; amount: number; percent: number }[]
  rateSet: Record<string, unknown>
}

export interface MonthClose extends Syncable {
  visibility: Visibility
  monthKey: string
  snapshotId: string | null
  closedAt: string | null
  reopenedAt: string | null
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
  income_source_amount: "incomeSourceAmounts",
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
  metal_lot: "metalLots",
  metal_disposal: "metalDisposals",
  metal_disposal_lot: "metalDisposalLots",
  market_override: "marketOverrides",
  market_setting: "marketSettings",
  cash_adjustment: "cashAdjustments",
  net_worth_snapshot: "netWorthSnapshots",
  month_close: "monthCloses",
  asset: "assets",
  asset_valuation: "assetValuations",
  bucket_move: "bucketMoves",
  privacy_setting: "privacySettings",
  notification_setting: "notificationSettings",
} as const

export type SyncTableName = keyof typeof SYNC_TABLES

export class MizanDatabase extends Dexie {
  cashAccounts!: EntityTable<CashAccount, "id">
  incomeSources!: EntityTable<IncomeSource, "id">
  incomeSourceAmounts!: EntityTable<IncomeSourceAmount, "id">
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
  metalLots!: EntityTable<MetalLot, "id">
  metalDisposals!: EntityTable<MetalDisposal, "id">
  metalDisposalLots!: EntityTable<MetalDisposalLot, "id">
  marketOverrides!: EntityTable<MarketOverride, "id">
  marketSettings!: EntityTable<MarketSetting, "id">
  cashAdjustments!: EntityTable<CashAdjustment, "id">
  netWorthSnapshots!: EntityTable<NetWorthSnapshot, "id">
  monthCloses!: EntityTable<MonthClose, "id">
  assets!: EntityTable<Asset, "id">
  assetValuations!: EntityTable<AssetValuation, "id">
  bucketMoves!: EntityTable<BucketMove, "id">
  privacySettings!: EntityTable<PrivacySetting, "id">
  notificationSettings!: EntityTable<NotificationSetting, "id">
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
    this.version(4).stores({
      metalLots: "id, deletedAt, metal, purchaseDate",
      metalDisposals: "id, deletedAt, metal, soldOn",
      metalDisposalLots: "id, deletedAt, disposalId, lotId",
      marketOverrides: "id, deletedAt, instrument, effectiveFrom",
      marketSettings: "id, deletedAt",
    })
    this.version(5).stores({
      incomeSourceAmounts: "id, deletedAt, incomeSourceId, effectiveFrom",
    })
    this.version(6).stores({
      cashAdjustments: "id, deletedAt, cashAccountId, adjustedOn",
      netWorthSnapshots: "id, deletedAt, monthKey, takenAt",
      monthCloses: "id, deletedAt, monthKey",
    })
    this.version(7).stores({
      assets: "id, deletedAt, type, status, sortOrder",
      assetValuations: "id, deletedAt, assetId, valuedOn",
    })
    this.version(8).stores({
      bucketMoves: "id, deletedAt, monthKey",
    })
    this.version(9).stores({
      privacySettings: "id, deletedAt",
    })
    this.version(10).stores({
      notificationSettings: "id, deletedAt",
    })
    this.version(11).stores({
      transactions: "id, deletedAt, monthKey, occurredOn, bucketId, type, [monthKey+bucketId], splitGroupId, assetId",
    })
  }
}

export const db = new MizanDatabase()

export function localTableFor(table: SyncTableName) {
  return db.table<Syncable, string>(SYNC_TABLES[table])
}
