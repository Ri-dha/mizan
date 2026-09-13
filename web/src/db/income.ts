import { db, type IncomeReceipt, type IncomeSource, type IncomeSourceAmount, type Visibility } from "./schema"
import { softDelete, writeFields } from "./write"
import { monthWindow } from "@/domain/calendar/month"
import { amountOn, plannedIncome, type Occurrence } from "@/domain/income/planned"

export const RATE_SCALE = 1_000_000

export interface IncomeSourceInput {
  name: string
  amount: number
  currency: string
  fxRateMicros: number
  frequency: IncomeSource["frequency"]
  payDay: number | null
  anchorDate: string | null
  activeFrom: string
  activeTo: string | null
  note: string | null
  visibility: Visibility
}

export interface ReceiptInput {
  incomeSourceId: string | null
  monthKey: string
  receivedOn: string
  amount: number
  currency: string
  fxRateMicros: number
  note: string | null
  visibility: Visibility
}

export function toBaseAmount(amount: number, fxRateMicros: number): number {
  return Math.round((amount * fxRateMicros) / RATE_SCALE)
}

export async function createIncomeSource(input: IncomeSourceInput): Promise<string> {
  const id = crypto.randomUUID()
  const sortOrder = await db.incomeSources.count()
  await db.transaction("rw", db.tables, async () => {
    await writeFields<IncomeSource>("income_source", id, { ...input, sortOrder })
    await writeFields<IncomeSourceAmount>("income_source_amount", crypto.randomUUID(), {
      visibility: input.visibility, incomeSourceId: id, effectiveFrom: input.activeFrom,
      amount: input.amount, currency: input.currency, fxRateMicros: input.fxRateMicros, note: null,
    })
  })
  return id
}

export function liveAmountHistory() {
  return db.incomeSourceAmounts.filter((a) => a.deletedAt === null).sortBy("effectiveFrom")
}

export function historyOf(source: IncomeSource, history: IncomeSourceAmount[]): IncomeSourceAmount[] {
  return history.filter((a) => a.incomeSourceId === source.id)
}

/**
 * A raise or a cut: appended to the history, and the source's own amount becomes the latest
 * known one. A source that predates the history gets its original amount recorded first, so
 * the months before the change keep planning on it.
 */
export async function recordAmountChange(source: IncomeSource, amount: number, effectiveFrom: string, note: string | null) {
  await db.transaction("rw", db.tables, async () => {
    const existing = await db.incomeSourceAmounts.where("incomeSourceId").equals(source.id).filter((a) => a.deletedAt === null).count()
    if (existing === 0) {
      await writeFields<IncomeSourceAmount>("income_source_amount", crypto.randomUUID(), {
        visibility: source.visibility, incomeSourceId: source.id, effectiveFrom: source.activeFrom, amount: source.amount,
        currency: source.currency, fxRateMicros: source.fxRateMicros, note: null,
      })
    }
    await writeFields<IncomeSourceAmount>("income_source_amount", crypto.randomUUID(), {
      visibility: source.visibility, incomeSourceId: source.id, effectiveFrom, amount,
      currency: source.currency, fxRateMicros: source.fxRateMicros, note,
    })
    await writeFields<IncomeSource>("income_source", source.id, { amount })
  })
}

export async function removeAmountChange(entry: IncomeSourceAmount, source: IncomeSource, history: IncomeSourceAmount[]) {
  await db.transaction("rw", db.tables, async () => {
    await softDelete("income_source_amount", entry.id)
    const remaining = historyOf(source, history).filter((a) => a.id !== entry.id).sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? -1 : 1))
    const latest = remaining[remaining.length - 1]
    if (latest) await writeFields<IncomeSource>("income_source", source.id, { amount: latest.amount })
  })
}

export function currentAmount(source: IncomeSource, history: IncomeSourceAmount[], today: string): number {
  return amountOn({ baseAmount: source.amount, amountSteps: historyOf(source, history).map((a) => ({ effectiveFrom: a.effectiveFrom, baseAmount: a.amount })) }, today)
}

export async function updateIncomeSource(id: string, input: Partial<IncomeSourceInput>) {
  await writeFields<IncomeSource>("income_source", id, input)
}

export async function recordReceipt(input: ReceiptInput): Promise<string> {
  const id = crypto.randomUUID()
  await writeFields<IncomeReceipt>("income_receipt", id, { ...input, baseAmount: toBaseAmount(input.amount, input.fxRateMicros) })
  return id
}

export function liveIncomeSources() {
  return db.incomeSources.filter((source) => source.deletedAt === null).sortBy("sortOrder")
}

export function liveReceiptsFor(monthKey: string) {
  return db.incomeReceipts.where("monthKey").equals(monthKey).filter((r) => r.deletedAt === null).toArray()
}

export interface PlannedOccurrence extends Occurrence {
  source: IncomeSource
  receipt: IncomeReceipt | undefined
}

/** Pay dates of the month with the receipt that settled each, if any (FR-INC-03). */
export function occurrencesFor(sources: IncomeSource[], receipts: IncomeReceipt[], monthKey: string, startDay: number, history: IncomeSourceAmount[] = []): PlannedOccurrence[] {
  const window = monthWindow(monthKey, startDay)
  const planned = plannedIncome(
    sources.map((s) => ({
      baseAmount: toBaseAmount(s.amount, s.fxRateMicros),
      frequency: s.frequency,
      payDay: s.payDay,
      anchorDate: s.anchorDate,
      activeFrom: s.activeFrom,
      activeTo: s.activeTo,
      amountSteps: historyOf(s, history).map((a) => ({ effectiveFrom: a.effectiveFrom, baseAmount: toBaseAmount(a.amount, a.fxRateMicros) })),
    })),
    window,
  )
  const unclaimed = [...receipts]
  return planned.occurrences.map((occurrence) => {
    const source = sources[occurrence.sourceIndex]
    const index = unclaimed.findIndex((r) => r.incomeSourceId === source.id)
    const receipt = index >= 0 ? unclaimed.splice(index, 1)[0] : undefined
    return { ...occurrence, source, receipt }
  })
}

export function monthIncome(occurrences: PlannedOccurrence[], receipts: IncomeReceipt[]) {
  return {
    planned: occurrences.reduce((sum, o) => sum + o.amount, 0),
    received: receipts.reduce((sum, r) => sum + r.baseAmount, 0),
  }
}
