import { db, type CashAccount, type CashAccountKind, type Visibility } from "./schema"
import { writeFields } from "./write"

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

export async function updateCashAccount(id: string, input: Partial<CashAccountInput>) {
  await writeFields<CashAccount>("cash_account", id, input)
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
