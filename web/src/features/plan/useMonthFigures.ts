import { useLiveQuery } from "dexie-react-hooks"

import { liveIncomeSources, liveReceiptsFor, monthIncome, occurrencesFor } from "@/db/income"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import type { Bucket, IncomeReceipt, IncomeSource, Plan } from "@/db/schema"
import { monthFigures, type MonthFiguresResult } from "@/domain/plan/figures"
import type { PlannedOccurrence } from "@/db/income"

export interface MonthView {
  plan: Plan | undefined
  buckets: Bucket[]
  sources: IncomeSource[]
  receipts: IncomeReceipt[]
  occurrences: PlannedOccurrence[]
  income: { planned: number; received: number }
  figures: MonthFiguresResult
}

const EMPTY: MonthView = {
  plan: undefined,
  buckets: [],
  sources: [],
  receipts: [],
  occurrences: [],
  income: { planned: 0, received: 0 },
  figures: { buckets: [], totalShareBasisPoints: 0, unallocatedPlanned: 0, unallocatedReceived: 0 },
}

/** Everything a month screen needs, recomputed whenever any underlying row changes. */
export function useMonthView(monthKey: string, startDay: number): MonthView {
  return useLiveQuery(async () => {
    const [plans, sources, receipts] = await Promise.all([livePlans(), liveIncomeSources(), liveReceiptsFor(monthKey)])
    const plan = planFor(plans, monthKey)
    const buckets = await liveBucketsOf(plan?.id)
    const occurrences = occurrencesFor(sources, receipts, monthKey, startDay)
    const income = monthIncome(occurrences, receipts)
    const figures = monthFigures({
      plannedIncome: income.planned,
      receivedIncome: income.received,
      buckets: buckets.map((b) => ({ id: b.id, shareBasisPoints: b.shareBasisPoints })),
      committed: {},
      spent: {},
    })
    return { plan, buckets, sources, receipts, occurrences, income, figures }
  }, [monthKey, startDay], EMPTY)
}
