import { Plus } from "lucide-react"
import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { useSelectedMonth } from "@/app/month"
import { Field } from "@/components/Field"
import { MonthPicker } from "@/components/MonthPicker"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { markBillPaid, undoBillPaid, type BillDue } from "@/db/expenses"
import type { RecurringExpense } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { formatMoney, fromMinorUnits, toMinorUnits } from "@/domain/money/format"
import { useMonthView } from "@/features/plan/useMonthFigures"
import { RecurringExpenseSheet } from "./RecurringExpenseSheet"

export function BillsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const base = session?.baseCurrency ?? "IQD"
  const [monthKey, setMonthKey] = useSelectedMonth(startDay)
  const view = useMonthView(monthKey, startDay)
  const [editing, setEditing] = useState<RecurringExpense | null | "new">(null)
  const [paying, setPaying] = useState<BillDue | null>(null)
  const [actual, setActual] = useState("")
  const [paidOn, setPaidOn] = useState(todayIso())
  const today = todayIso()
  const money = (amount: number, currency = base) => formatMoney(amount, currency, i18n.language)
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { day: "numeric", month: "short" }).format(new Date(iso))
  const bucketName = (id: string | null) => view.buckets.find((b) => b.id === id)?.name

  const committed = view.bills.filter((b) => !b.occurrence).reduce((sum, b) => sum + b.expectedAmount, 0)
  const paid = view.bills.filter((b) => b.occurrence).reduce((sum, b) => sum + (b.occurrence?.actualAmount ?? 0), 0)

  function startPaying(bill: BillDue) {
    setPaying(bill)
    setActual(fromMinorUnits(bill.expense.amount, bill.expense.currency))
    setPaidOn(bill.dueDate <= today ? bill.dueDate : today)
  }

  async function confirmPaid(event: FormEvent) {
    event.preventDefault()
    if (!paying) return
    await markBillPaid(paying, monthKey, toMinorUnits(actual, paying.expense.currency), paidOn)
    setPaying(null)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("bills.title")}</h1>
        <MonthPicker value={monthKey} onChange={setMonthKey} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("bills.thisMonth")}</CardTitle>
          <CardDescription>{t("bills.stillDue")}: {money(committed)} · {t("bills.paid")}: {money(paid)}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {view.bills.length === 0 && <p className="opacity-70">{t("bills.nothingDue")}</p>}
          {view.bills.map((bill) => (
            <div key={`${bill.expense.id}-${bill.dueDate}`} className="flex items-center justify-between gap-3 rounded-base border-2 border-border p-3">
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-heading">{bill.expense.name}</span>
                <span className="truncate text-sm opacity-70">
                  {bill.occurrence ? t("bills.paidOn", { date: date(bill.occurrence.paidOn) }) : t("bills.dueOn", { date: date(bill.dueDate) })}
                  {bucketName(bill.expense.bucketId) ? ` · ${bucketName(bill.expense.bucketId)}` : ""}
                </span>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span className="tabular-nums font-heading">
                  {bill.expense.isEstimate && !bill.occurrence ? "≈ " : ""}{money(bill.occurrence?.actualAmount ?? bill.expectedAmount)}
                </span>
                {bill.occurrence ? (
                  <Button size="sm" variant="neutral" onClick={() => void undoBillPaid(bill.occurrence!)}>{t("bills.undo")}</Button>
                ) : (
                  <Button size="sm" variant={bill.dueDate < today ? "default" : "neutral"} onClick={() => startPaying(bill)}>
                    {bill.dueDate < today ? t("bills.overdue") : t("bills.markPaid")}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>{t("bills.recurring")}</CardTitle>
          <Button size="sm" onClick={() => setEditing("new")}><Plus /> {t("bills.add")}</Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {view.bills.length === 0 && <p className="opacity-70">{t("bills.none")}</p>}
          {[...new Map(view.bills.map((b) => [b.expense.id, b.expense])).values()].map((expense) => (
            <div key={expense.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => setEditing(expense)}>
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-heading">{expense.name}</span>
                <span className="truncate text-sm opacity-70">{t(`bills.frequencies.${expense.frequency}`)}{bucketName(expense.bucketId) ? ` · ${bucketName(expense.bucketId)}` : ""}</span>
              </div>
              <div className="flex items-center gap-2">
                {expense.isEstimate && <Badge variant="neutral">{t("bills.estimate")}</Badge>}
                <span className="tabular-nums font-heading">{money(expense.amount, expense.currency)}</span>
              </div>
            </div>
          ))}
          <RecurringExpenseListOutsideMonth monthBills={view.bills} onEdit={setEditing} />
        </CardContent>
      </Card>

      <RecurringExpenseSheet open={editing !== null} expense={editing === "new" ? null : editing} buckets={view.buckets} onClose={() => setEditing(null)} />

      <Dialog open={paying !== null} onOpenChange={(open) => !open && setPaying(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t("bills.markPaid")}: {paying?.expense.name}</DialogTitle></DialogHeader>
          <form onSubmit={confirmPaid} className="flex flex-col gap-4">
            <Field id="actual" label={t("bills.actualAmount")} hint={paying?.expense.isEstimate ? t("bills.estimateHint", { amount: money(paying.expectedAmount) }) : undefined}>
              <Input id="actual" inputMode="decimal" dir="ltr" value={actual} onChange={(e) => setActual(e.target.value)} required />
            </Field>
            <Field id="paidOn" label={t("bills.paidOnLabel")}>
              <Input id="paidOn" type="date" dir="ltr" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} required />
            </Field>
            <DialogFooter><Button type="submit">{t("accounts.save")}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

/** Bills that exist but have no due date this month (ended, quarterly, annual) still need to be reachable. */
function RecurringExpenseListOutsideMonth({ monthBills, onEdit }: { monthBills: BillDue[]; onEdit: (e: RecurringExpense) => void }) {
  const { t } = useTranslation()
  const shown = new Set(monthBills.map((b) => b.expense.id))
  const others = useOthers(shown)
  if (others.length === 0) return null
  return (
    <>
      <p className="mt-2 text-sm opacity-70">{t("bills.notThisMonth")}</p>
      {others.map((expense) => (
        <div key={expense.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-dashed border-border p-3" onClick={() => onEdit(expense)}>
          <span className="truncate font-heading">{expense.name}</span>
          <span className="text-sm opacity-70">{t(`bills.frequencies.${expense.frequency}`)}</span>
        </div>
      ))}
    </>
  )
}

import { useLiveQuery } from "dexie-react-hooks"
import { liveRecurringExpenses } from "@/db/expenses"

function useOthers(shown: Set<string>) {
  const all = useLiveQuery(liveRecurringExpenses, [], [])
  return all.filter((e) => !shown.has(e.id))
}
