import type { RecurringExpense } from "@/db/schema"

/** Category labels that mark a recurring expense as a subscription, in either language, any case. */
const SUBSCRIPTION_CATEGORIES: ReadonlySet<string> = new Set(["subscriptions", "subscription", "اشتراك", "اشتراكات"])
export const SUBSCRIPTION_CATEGORY = "Subscriptions"

const DAYS_PER_YEAR = 365.25
const MONTHS_PER_YEAR = 12
const RATE_SCALE = 1_000_000
const CENTS_PER_DOLLAR = 100

export function isSubscription(expense: RecurringExpense): boolean {
  return SUBSCRIPTION_CATEGORIES.has((expense.category ?? "").trim().toLowerCase())
}

/** What the plan costs per year in its own currency, so monthly and annual plans compare fairly. */
export function yearlyCost(expense: RecurringExpense): number {
  switch (expense.frequency) {
    case "MONTHLY": return expense.amount * MONTHS_PER_YEAR
    case "QUARTERLY": return expense.amount * 4
    case "ANNUAL": return expense.amount
    case "CUSTOM": return expense.intervalDays && expense.intervalDays > 0 ? Math.round((expense.amount * DAYS_PER_YEAR) / expense.intervalDays) : 0
  }
}

export function monthlyCost(expense: RecurringExpense): number {
  return Math.round(yearlyCost(expense) / MONTHS_PER_YEAR)
}

export interface SubscriptionLine {
  expense: RecurringExpense
  monthly: number
  yearly: number
  /** Converted at the rate frozen on the bill (BR-09). */
  monthlyBase: number
  yearlyBase: number
}

export interface SubscriptionSummary {
  lines: SubscriptionLine[]
  monthlyBase: number
  yearlyBase: number
  /** In cents at today's rate; null when no rate is known. */
  monthlyUsd: number | null
  yearlyUsd: number | null
}

const toBase = (amount: number, fxRateMicros: number) => Math.round((amount * fxRateMicros) / RATE_SCALE)

/** Active subscriptions on a day, with monthly and yearly equivalents in base currency and dollars. */
export function subscriptionSummary(expenses: RecurringExpense[], today: string, usdIqdMicros: number): SubscriptionSummary {
  const lines = expenses
    .filter((e) => isSubscription(e) && e.activeFrom <= today && (e.activeTo === null || e.activeTo >= today))
    .map((expense) => {
      const monthly = monthlyCost(expense)
      const yearly = yearlyCost(expense)
      return { expense, monthly, yearly, monthlyBase: toBase(monthly, expense.fxRateMicros), yearlyBase: toBase(yearly, expense.fxRateMicros) }
    })
    .sort((a, b) => b.monthlyBase - a.monthlyBase)
  const monthlyBase = lines.reduce((s, l) => s + l.monthlyBase, 0)
  const yearlyBase = lines.reduce((s, l) => s + l.yearlyBase, 0)
  const toUsd = (base: number) => (usdIqdMicros > 0 ? Math.round((base * RATE_SCALE * CENTS_PER_DOLLAR) / usdIqdMicros) : null)
  return { lines, monthlyBase, yearlyBase, monthlyUsd: toUsd(monthlyBase), yearlyUsd: toUsd(yearlyBase) }
}
