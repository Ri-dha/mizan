import { db, type CashAccount, type CashAccountKind, type Visibility } from "./schema"
import { writeFields } from "./write"
import { recordCashAdjustment } from "./networth"
import { todayIso } from "@/domain/calendar/month"

export interface CashAccountInput {
  name: string
  kind: CashAccountKind
  institution: string | null
  balance: number
  currency: string
  visibility: Visibility
}

export async function createCashAccount(input: CashAccountInput): Promise<string> {
  const id = crypto.randomUUID()
  const sortOrder = await db.cashAccounts.count()
  await writeFields<CashAccount>("cash_account", id, { ...input, sortOrder })
  return id
}

export async function updateCashAccount(id: string, input: Partial<CashAccountInput>, adjustmentNote: string | null = null) {
  const existing = await db.cashAccounts.get(id)
  await db.transaction("rw", db.tables, async () => {
    if (existing && input.balance !== undefined && input.balance !== existing.balance) {
      await recordCashAdjustment(existing, input.balance, todayIso(), adjustmentNote)
    }
    await writeFields<CashAccount>("cash_account", id, input)
  })
}

export function liveAdjustmentsOf(accountId: string) {
  return db.cashAdjustments.where("cashAccountId").equals(accountId).filter((a) => a.deletedAt === null).reverse().sortBy("adjustedOn")
}

export function liveCashAccounts() {
  return db.cashAccounts.filter((account) => account.deletedAt === null).sortBy("sortOrder")
}

export function totalsByCurrency(accounts: CashAccount[]): Record<string, number> {
  return accounts.reduce<Record<string, number>>((totals, account) => {
    totals[account.currency] = (totals[account.currency] ?? 0) + account.balance
    return totals
  }, {})
}
