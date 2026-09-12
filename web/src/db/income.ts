import { db, type IncomeReceipt, type IncomeSource, type Visibility } from "./schema"
import { writeFields } from "./write"
import { monthWindow } from "@/domain/calendar/month"
import { plannedIncome, type Occurrence } from "@/domain/income/planned"

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
  await writeFields<IncomeSource>("income_source", id, { ...input, sortOrder })
  return id
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
export function occurrencesFor(sources: IncomeSource[], receipts: IncomeReceipt[], monthKey: string, startDay: number): PlannedOccurrence[] {
  const window = monthWindow(monthKey, startDay)
  const planned = plannedIncome(
    sources.map((s) => ({
      baseAmount: toBaseAmount(s.amount, s.fxRateMicros),
      frequency: s.frequency,
      payDay: s.payDay,
      anchorDate: s.anchorDate,
      activeFrom: s.activeFrom,
      activeTo: s.activeTo,
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
