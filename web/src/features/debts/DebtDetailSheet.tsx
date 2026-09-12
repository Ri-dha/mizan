import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { balanceOf, recordPayment, undoPayment } from "@/db/debts"
import type { Debt, DebtPayment } from "@/db/schema"
import { monthKeyFor, todayIso } from "@/domain/calendar/month"
import { debtPayoff } from "@/domain/debt/payoff"
import { formatMoney, fromMinorUnits, toMinorUnits } from "@/domain/money/format"

interface Props {
  debt: Debt | null
  payments: DebtPayment[]
  startDay: number
  onClose: () => void
  onEdit: (debt: Debt) => void
}

export function DebtDetailSheet({ debt, payments, startDay, onClose, onEdit }: Props) {
  const { t, i18n } = useTranslation()
  const [amount, setAmount] = useState("")
  const [paidOn, setPaidOn] = useState(todayIso())
  const [extra, setExtra] = useState("")

  if (!debt) return <Sheet open={false}><SheetContent /></Sheet>

  const money = (value: number) => formatMoney(value, debt.currency, i18n.language)
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso))
  const balance = balanceOf(debt, payments)
  const ledger = payments.filter((p) => p.debtId === debt.id).reverse()
  const payoff = debtPayoff(balance, debt.annualRateBasisPoints, debt.monthlyPayment)
  const extraMinor = extra ? toMinorUnits(extra, debt.currency) : 0
  const withExtra = extraMinor > 0 ? debtPayoff(balance, debt.annualRateBasisPoints, debt.monthlyPayment + extraMinor) : null

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!debt) return
    const paid = toMinorUnits(amount || fromMinorUnits(debt.monthlyPayment, debt.currency), debt.currency)
    await recordPayment(debt, payments, paid, paidOn, monthKeyFor(paidOn, startDay), null)
    setAmount("")
  }

  return (
    <Sheet open onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{debt.name}</SheetTitle></SheetHeader>
        <div className="flex flex-col gap-4 px-4">
          <div className="rounded-base border-2 border-border p-3">
            <p className="text-sm opacity-70">{t("debts.balance")}</p>
            <p className="text-2xl font-heading tabular-nums">{money(balance)}</p>
            <p className="text-sm">
              {debt.status === "SETTLED"
                ? t("debts.settled")
                : payoff.neverClears
                  ? t("debts.neverClearsLong", { payment: money(debt.monthlyPayment) })
                  : t("debts.payoffLong", { months: payoff.months, total: money(payoff.totalPaid), interest: money(payoff.totalInterest) })}
            </p>
          </div>

          {debt.status === "ACTIVE" && (
            <form onSubmit={submit} className="flex flex-col gap-3 rounded-base border-2 border-border p-3">
              <p className="font-heading">{t("debts.recordPayment")}</p>
              <div className="grid grid-cols-2 gap-2">
                <Field id="amount" label={t("income.amount")}>
                  <Input id="amount" inputMode="decimal" dir="ltr" placeholder={fromMinorUnits(debt.monthlyPayment, debt.currency)} value={amount} onChange={(e) => setAmount(e.target.value)} />
                </Field>
                <Field id="paidOn" label={t("bills.paidOnLabel")}>
                  <Input id="paidOn" type="date" dir="ltr" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} required />
                </Field>
              </div>
              <Button type="submit" className="self-start">{t("debts.recordPayment")}</Button>
            </form>
          )}

          {debt.status === "ACTIVE" && !payoff.neverClears && (
            <div className="flex flex-col gap-2 rounded-base border-2 border-border p-3">
              <p className="font-heading">{t("debts.whatIf")}</p>
              <Field id="extra" label={t("debts.extraPerMonth")}>
                <Input id="extra" inputMode="decimal" dir="ltr" value={extra} onChange={(e) => setExtra(e.target.value)} />
              </Field>
              {withExtra && !withExtra.neverClears && (
                <p className="text-sm">{t("debts.whatIfResult", { months: payoff.months - withExtra.months, interest: money(payoff.totalInterest - withExtra.totalInterest) })}</p>
              )}
            </div>
          )}

          <div className="flex flex-col gap-2">
            <p className="font-heading">{t("debts.ledger")}</p>
            {ledger.length === 0 && <p className="text-sm opacity-70">{t("debts.noPayments")}</p>}
            {ledger.map((p) => (
              <div key={p.id} className="flex items-center justify-between gap-2 rounded-base border-2 border-border p-2 text-sm">
                <span>{date(p.paidOn)} · {t("debts.principalPart", { amount: money(p.principalComponent) })}{p.interestComponent ? ` · ${t("debts.interestPart", { amount: money(p.interestComponent) })}` : ""}</span>
                <span className="flex items-center gap-2 tabular-nums font-heading">{money(p.amount)}
                  <Button size="sm" variant="neutral" onClick={() => void undoPayment(p)}>{t("bills.undo")}</Button>
                </span>
              </div>
            ))}
          </div>

          <Button variant="neutral" onClick={() => onEdit(debt)}>{t("debts.edit")}</Button>
        </div>
      </SheetContent>
    </Sheet>
  )
}
