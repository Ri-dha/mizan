import { Plus } from "lucide-react"
import { useLiveQuery } from "dexie-react-hooks"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { balanceOf, liveDebts, livePayments } from "@/db/debts"
import type { Debt } from "@/db/schema"
import { debtPayoff } from "@/domain/debt/payoff"
import { compareStrategies } from "@/domain/debt/strategy"
import { Field } from "@/components/Field"
import { Input } from "@/components/ui/input"
import { toMinorUnits } from "@/domain/money/format"
import { formatMoney } from "@/domain/money/format"
import { DebtDetailSheet } from "./DebtDetailSheet"
import { DebtSheet } from "./DebtSheet"

export function DebtsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const debts = useLiveQuery(liveDebts, [], [])
  const payments = useLiveQuery(livePayments, [], [])
  const [editing, setEditing] = useState<Debt | null | "new">(null)
  const [detail, setDetail] = useState<Debt | null>(null)
  const [extra, setExtra] = useState("")
  const base = session?.baseCurrency ?? "IQD"
  const active = debts.filter((d) => d.direction === "OWING" && d.status === "ACTIVE" && d.currency === base)
  const strategies = active.length > 1 ? compareStrategies(active.map((d) => ({ id: d.id, balance: Math.max(0, balanceOf(d, payments)), annualRateBasisPoints: d.annualRateBasisPoints, monthlyPayment: d.monthlyPayment })), extra ? toMinorUnits(extra, base) : 0) : null
  const nameOf = (id: string) => debts.find((d) => d.id === id)?.name ?? id

  const money = (amount: number, currency: string) => formatMoney(amount, currency, i18n.language)
  const owing = debts.filter((d) => d.direction === "OWING")
  const owed = debts.filter((d) => d.direction === "OWED")

  function row(debt: Debt) {
    const balance = balanceOf(debt, payments)
    const payoff = debtPayoff(balance, debt.annualRateBasisPoints, debt.monthlyPayment)
    return (
      <div key={debt.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => setDetail(debt)}>
        <div className="flex min-w-0 flex-col">
          <span className="truncate font-heading">{debt.name}{debt.counterparty ? ` · ${debt.counterparty}` : ""}</span>
          <span className="truncate text-sm opacity-70">
            {debt.status === "SETTLED"
              ? t("debts.settled")
              : payoff.neverClears
                ? t("debts.neverClears")
                : t("debts.payoffSummary", { months: payoff.months, interest: money(payoff.totalInterest, debt.currency) })}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {debt.annualRateBasisPoints === 0 && <Badge variant="neutral">{t("debts.interestFree")}</Badge>}
          <span className="tabular-nums font-heading">{money(balance, debt.currency)}</span>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl">{t("debts.title")}</h1>
        <Button onClick={() => setEditing("new")}><Plus /> {t("debts.add")}</Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("debts.owing")}</CardTitle>
          <CardDescription>{t("debts.owingBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {owing.length === 0 && <p className="opacity-70">{t("debts.none")}</p>}
          {owing.map(row)}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("debts.owed")}</CardTitle>
          <CardDescription>{t("debts.owedBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {owed.length === 0 && <p className="opacity-70">{t("debts.none")}</p>}
          {owed.map(row)}
        </CardContent>
      </Card>

      {strategies && (
        <Card>
          <CardHeader>
            <CardTitle>{t("debts.strategyTitle")}</CardTitle>
            <CardDescription>{t("debts.strategyBody")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <Field id="strategyExtra" label={t("debts.extraPerMonth")}>
              <Input id="strategyExtra" inputMode="decimal" dir="ltr" value={extra} onChange={(e) => setExtra(e.target.value)} className="w-40" />
            </Field>
            <div className="grid gap-2 sm:grid-cols-2">
              {(["snowball", "avalanche"] as const).map((name) => {
                const outcome = strategies[name]
                return (
                  <div key={name} className="rounded-base border-2 border-border p-3 text-sm">
                    <p className="font-heading">{t(`debts.strategies.${name}`)}</p>
                    <p className="opacity-70">{t(`debts.strategyHints.${name}`)}</p>
                    <p className="mt-1">{outcome.order.map(nameOf).join(" → ")}</p>
                    <p className="mt-1 font-heading">{outcome.neverClears ? t("debts.neverClears") : t("debts.strategyOutcome", { months: outcome.months, interest: money(outcome.totalInterest, base) })}</p>
                  </div>
                )
              })}
            </div>
            {!strategies.snowball.neverClears && !strategies.avalanche.neverClears && (
              <p className="text-sm">{t("debts.strategyDifference", { amount: money(Math.abs(strategies.snowball.totalInterest - strategies.avalanche.totalInterest), base) })}</p>
            )}
          </CardContent>
        </Card>
      )}

      <DebtSheet open={editing !== null} debt={editing === "new" ? null : editing} onClose={() => setEditing(null)} />
      <DebtDetailSheet debt={detail} payments={payments} startDay={startDay} onClose={() => setDetail(null)} onEdit={(d) => { setDetail(null); setEditing(d) }} />
    </div>
  )
}
