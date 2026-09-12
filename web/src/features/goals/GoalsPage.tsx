import { Plus } from "lucide-react"
import { useLiveQuery } from "dexie-react-hooks"
import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { liveDeposits, liveGoals, recordDeposit, savedOf, undoDeposit } from "@/db/goals"
import type { Goal, GoalDeposit } from "@/db/schema"
import { monthKeyFor, todayIso } from "@/domain/calendar/month"
import { goalProjection } from "@/domain/goal/projection"
import { formatMoney, toMinorUnits } from "@/domain/money/format"
import { GoalSheet } from "./GoalSheet"

export function GoalsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const goals = useLiveQuery(liveGoals, [], [])
  const deposits = useLiveQuery(liveDeposits, [], [])
  const [editing, setEditing] = useState<Goal | null | "new">(null)
  const [depositing, setDepositing] = useState<{ goal: Goal; direction: GoalDeposit["direction"] } | null>(null)
  const [amount, setAmount] = useState("")
  const [on, setOn] = useState(todayIso())
  const today = todayIso()

  const money = (value: number, currency: string) => formatMoney(value, currency, i18n.language)
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso))

  async function submitDeposit(event: FormEvent) {
    event.preventDefault()
    if (!depositing) return
    await recordDeposit(depositing.goal, deposits, toMinorUnits(amount, depositing.goal.currency), depositing.direction, on, monthKeyFor(on, startDay), null)
    setDepositing(null)
    setAmount("")
  }

  const active = goals.filter((g) => g.status !== "ARCHIVED")

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl">{t("goals.title")}</h1>
        <Button onClick={() => setEditing("new")}><Plus /> {t("goals.add")}</Button>
      </div>

      {active.length === 0 && <p className="opacity-70">{t("goals.none")}</p>}
      {active.map((goal) => {
        const saved = savedOf(goal, deposits)
        const projection = goalProjection(goal.targetAmount, saved, goal.monthlyContribution, goal.targetDate, today)
        const ledger = deposits.filter((d) => d.goalId === goal.id).slice(-3).reverse()
        return (
          <Card key={goal.id}>
            <CardHeader className="flex-row items-start justify-between gap-2">
              <div>
                <CardTitle className="cursor-pointer" onClick={() => setEditing(goal)}>{goal.name}</CardTitle>
                <p className="text-sm opacity-70">
                  {money(saved, goal.currency)} / {money(goal.targetAmount, goal.currency)} · {projection.percent}%
                </p>
              </div>
              {goal.status === "COMPLETED" && <Badge>{t("goals.completed")}</Badge>}
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <div className="h-4 w-full overflow-hidden rounded-base border-2 border-border bg-secondary-background">
                <div className="h-full bg-main" style={{ width: `${projection.percent}%` }} />
              </div>
              <p className="text-sm">
                {goal.status === "COMPLETED" && goal.completedOn
                  ? t("goals.completedOn", { date: date(goal.completedOn) })
                  : projection.projectedDate
                    ? t("goals.projected", { date: date(projection.projectedDate), months: projection.monthsToTarget })
                    : projection.requiredMonthly !== null && goal.targetDate
                      ? t("goals.required", { amount: money(projection.requiredMonthly, goal.currency), date: date(goal.targetDate) })
                      : t("goals.noProjection")}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" onClick={() => setDepositing({ goal, direction: "DEPOSIT" })}>{t("goals.deposit")}</Button>
                <Button size="sm" variant="neutral" onClick={() => setDepositing({ goal, direction: "WITHDRAWAL" })}>{t("goals.withdraw")}</Button>
              </div>
              {ledger.map((d) => (
                <div key={d.id} className="flex items-center justify-between gap-2 text-sm">
                  <span>{date(d.depositedOn)} · {t(`goals.directions.${d.direction}`)}</span>
                  <span className="flex items-center gap-2 tabular-nums">{money(d.amount, goal.currency)}
                    <Button size="sm" variant="neutral" onClick={() => void undoDeposit(d)}>{t("bills.undo")}</Button>
                  </span>
                </div>
              ))}
            </CardContent>
          </Card>
        )
      })}

      <GoalSheet open={editing !== null} goal={editing === "new" ? null : editing} onClose={() => setEditing(null)} />

      <Dialog open={depositing !== null} onOpenChange={(open) => !open && setDepositing(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{depositing ? t(`goals.${depositing.direction === "DEPOSIT" ? "deposit" : "withdraw"}`) : ""}: {depositing?.goal.name}</DialogTitle></DialogHeader>
          <form onSubmit={submitDeposit} className="flex flex-col gap-4">
            <Field id="amount" label={t("income.amount")}>
              <Input id="amount" inputMode="decimal" dir="ltr" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
            </Field>
            <Field id="on" label={t("transactions.date")}>
              <Input id="on" type="date" dir="ltr" value={on} onChange={(e) => setOn(e.target.value)} required />
            </Field>
            <DialogFooter><Button type="submit">{t("accounts.save")}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
