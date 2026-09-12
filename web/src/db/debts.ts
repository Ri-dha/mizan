import { db, type Debt, type DebtPayment, type Visibility } from "./schema"
import { softDelete, writeFields } from "./write"
import { toBaseAmount } from "./income"
import { createTransaction, deleteTransaction } from "./transactions"
import { monthlyInterest } from "@/domain/debt/payoff"

export interface DebtInput {
  name: string
  counterparty: string | null
  direction: Debt["direction"]
  principal: number
  currency: string
  fxRateMicros: number
  annualRateBasisPoints: number
  termMonths: number | null
  monthlyPayment: number
  bucketId: string | null
  startDate: string
  note: string | null
  visibility: Visibility
}

export async function createDebt(input: DebtInput): Promise<string> {
  const id = crypto.randomUUID()
  await writeFields<Debt>("debt", id, { ...input, status: "ACTIVE" })
  return id
}

export async function updateDebt(id: string, input: Partial<DebtInput>) {
  await writeFields<Debt>("debt", id, input)
}

export function liveDebts() {
  return db.debts.filter((d) => d.deletedAt === null).toArray()
}

export function livePayments() {
  return db.debtPayments.filter((p) => p.deletedAt === null).sortBy("paidOn")
}

/** FR-DBT-04: the balance is the ledger, never an edited number. */
export function balanceOf(debt: Debt, payments: DebtPayment[]): number {
  return payments.filter((p) => p.debtId === debt.id).reduce((balance, p) => balance - p.principalComponent, debt.principal)
}

export function baseBalanceOf(debt: Debt, payments: DebtPayment[]): number {
  return toBaseAmount(balanceOf(debt, payments), debt.fxRateMicros)
}

/**
 * Interest for the month is charged on the balance before the payment and frozen on the
 * ledger row, so a later rate change never rewrites what was paid (BR-09).
 */
export async function recordPayment(debt: Debt, payments: DebtPayment[], amount: number, paidOn: string, monthKey: string, note: string | null) {
  const balanceBefore = balanceOf(debt, payments)
  const interest = monthlyInterest(balanceBefore, debt.annualRateBasisPoints)
  await db.transaction("rw", db.tables, async () => {
    const transactionId = await createTransaction({
      type: "TRANSFER",
      occurredOn: paidOn,
      monthKey,
      amount,
      currency: debt.currency,
      fxRateMicros: debt.fxRateMicros,
      bucketId: debt.direction === "OWING" ? debt.bucketId : null,
      category: null,
      payee: debt.counterparty ?? debt.name,
      note,
      counterpartyType: "DEBT",
      counterpartyId: debt.id,
      attachmentId: null,
      visibility: debt.visibility,
    })
    await writeFields<DebtPayment>("debt_payment", crypto.randomUUID(), {
      visibility: debt.visibility,
      debtId: debt.id,
      paidOn,
      monthKey,
      amount,
      interestComponent: interest,
      principalComponent: amount - interest,
      transactionId,
      note,
    })
    if (balanceBefore - (amount - interest) <= 0 && debt.status === "ACTIVE") {
      await writeFields<Debt>("debt", debt.id, { status: "SETTLED" })
    }
  })
}

export async function undoPayment(payment: DebtPayment) {
  await db.transaction("rw", db.tables, async () => {
    if (payment.transactionId) await deleteTransaction(payment.transactionId)
    await softDelete("debt_payment", payment.id)
    await writeFields<Debt>("debt", payment.debtId, { status: "ACTIVE" })
  })
}

/** FR-DBT-06: a scheduled payment commits its bucket until the month's payment is recorded. */
export function committedByDebts(debts: Debt[], payments: DebtPayment[], monthKey: string): Record<string, number> {
  const committed: Record<string, number> = {}
  for (const debt of debts) {
    if (debt.direction !== "OWING" || debt.status !== "ACTIVE" || !debt.bucketId || debt.monthlyPayment <= 0) continue
    if (payments.some((p) => p.debtId === debt.id && p.monthKey === monthKey)) continue
    committed[debt.bucketId] = (committed[debt.bucketId] ?? 0) + toBaseAmount(debt.monthlyPayment, debt.fxRateMicros)
  }
  return committed
}
