import { useLiveQuery } from "dexie-react-hooks"

import { balanceOf, committedByDebts, liveDebts, livePayments } from "@/db/debts"
import { billsDue, committedByBills, liveOccurrencesFor, liveRecurringExpenses, type BillDue } from "@/db/expenses"
import { committedByGoals, liveDeposits, liveGoals, savedOf } from "@/db/goals"
import { liveAmountHistory, liveIncomeSources, liveReceiptsFor, monthIncome, occurrencesFor, type PlannedOccurrence } from "@/db/income"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import type { Bucket, Debt, DebtPayment, Goal, GoalDeposit, IncomeReceipt, IncomeSource, IncomeSourceAmount, LedgerTransaction, Plan } from "@/db/schema"
import { liveTransactionsFor, spentByBucket, transfersByBucket } from "@/db/transactions"
import { monthFigures, type MonthFiguresResult } from "@/domain/plan/figures"

export interface MonthView {
  plan: Plan | undefined
  buckets: Bucket[]
  sources: IncomeSource[]
  amountHistory: IncomeSourceAmount[]
  receipts: IncomeReceipt[]
  occurrences: PlannedOccurrence[]
  income: { planned: number; received: number }
  bills: BillDue[]
  transactions: LedgerTransaction[]
  debts: Debt[]
  debtPayments: DebtPayment[]
  goals: Goal[]
  goalDeposits: GoalDeposit[]
  figures: MonthFiguresResult
}

const EMPTY: MonthView = {
  plan: undefined, buckets: [], sources: [], amountHistory: [], receipts: [], occurrences: [],
  income: { planned: 0, received: 0 }, bills: [], transactions: [], debts: [], debtPayments: [], goals: [], goalDeposits: [],
  figures: { buckets: [], totalShareBasisPoints: 0, unallocatedPlanned: 0, unallocatedReceived: 0 },
}

function sumMaps(...maps: Record<string, number>[]): Record<string, number> {
  const total: Record<string, number> = {}
  for (const map of maps) for (const [key, value] of Object.entries(map)) total[key] = (total[key] ?? 0) + value
  return total
}

/** Everything a month screen needs, recomputed whenever any underlying row changes. */
export function useMonthView(monthKey: string, startDay: number): MonthView {
  return useLiveQuery(async () => {
    const [plans, sources, amountHistory, receipts, expenses, paidOccurrences, transactions, debts, debtPayments, goals, goalDeposits] = await Promise.all([
      livePlans(), liveIncomeSources(), liveAmountHistory(), liveReceiptsFor(monthKey), liveRecurringExpenses(), liveOccurrencesFor(monthKey),
      liveTransactionsFor(monthKey), liveDebts(), livePayments(), liveGoals(), liveDeposits(),
    ])
    const plan = planFor(plans, monthKey)
    const buckets = await liveBucketsOf(plan?.id)
    const occurrences = occurrencesFor(sources, receipts, monthKey, startDay, amountHistory)
    const income = monthIncome(occurrences, receipts)
    const bills = billsDue(expenses, paidOccurrences, monthKey, startDay)
    const { transfersIn, transfersOut } = transfersByBucket(transactions)
    const figures = monthFigures({
      plannedIncome: income.planned,
      receivedIncome: income.received,
      buckets: buckets.map((b) => ({ id: b.id, shareBasisPoints: b.shareBasisPoints })),
      committed: sumMaps(committedByBills(bills), committedByDebts(debts, debtPayments, monthKey), committedByGoals(goals, goalDeposits, monthKey)),
      spent: spentByBucket(transactions),
      transfersIn,
      transfersOut,
    })
    return { plan, buckets, sources, amountHistory, receipts, occurrences, income, bills, transactions, debts, debtPayments, goals, goalDeposits, figures }
  }, [monthKey, startDay], EMPTY)
}

export { balanceOf, savedOf }
