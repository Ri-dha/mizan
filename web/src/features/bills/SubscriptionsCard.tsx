import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { liveRecurringExpenses } from "@/db/expenses"
import type { RecurringExpense } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { subscriptionSummary } from "@/domain/expense/subscriptions"
import { formatMoney } from "@/domain/money/format"
import { useMetals } from "@/features/metals/useMetals"

/** Every bill in the Subscriptions category, what each costs per month and per year, and the totals in dinars and dollars. */
export function SubscriptionsCard({ currency, onEdit }: { currency: string; onEdit: (expense: RecurringExpense) => void }) {
  const { t, i18n } = useTranslation()
  const expenses = useLiveQuery(liveRecurringExpenses, [], [])
  const { prices } = useMetals()
  const summary = subscriptionSummary(expenses, todayIso(), prices.usdIqdMicros)
  const money = (v: number, c = currency) => formatMoney(v, c, i18n.language)

  return (
    <Card data-tour="bills-subscriptions">
      <CardHeader>
        <CardTitle>{t("subscriptions.title")}</CardTitle>
        <CardDescription>
          {summary.lines.length === 0
            ? t("subscriptions.empty")
            : t("subscriptions.totals", { monthly: money(summary.monthlyBase), yearly: money(summary.yearlyBase) })}
          {summary.lines.length > 0 && summary.monthlyUsd !== null && (
            <span dir="ltr"> · {t("subscriptions.totalsUsd", { monthly: money(summary.monthlyUsd, "USD"), yearly: money(summary.yearlyUsd ?? 0, "USD") })}</span>
          )}
        </CardDescription>
      </CardHeader>
      {summary.lines.length > 0 && (
        <CardContent className="flex flex-col gap-2">
          {summary.lines.map((line) => (
            <div key={line.expense.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => onEdit(line.expense)}>
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-heading">{line.expense.name}</span>
                <span className="truncate text-sm opacity-70">
                  {money(line.expense.amount, line.expense.currency)} · {t(`bills.frequencies.${line.expense.frequency}`)}
                  {line.expense.currency !== currency && ` · ${t("subscriptions.perMonthBase", { amount: money(line.monthlyBase) })}`}
                </span>
              </div>
              <div className="flex shrink-0 flex-col items-end text-sm">
                <span className="tabular-nums font-heading">{t("subscriptions.perMonth", { amount: money(line.monthlyBase) })}</span>
                <span className="tabular-nums opacity-70">{t("subscriptions.perYear", { amount: money(line.yearlyBase) })}</span>
              </div>
            </div>
          ))}
        </CardContent>
      )}
    </Card>
  )
}
