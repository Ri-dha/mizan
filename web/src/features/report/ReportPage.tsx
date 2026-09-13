import { Printer } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { useSession } from "@/api/auth"
import { shiftMonth, useSelectedMonth } from "@/app/month"
import { MonthPicker } from "@/components/MonthPicker"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { formatMoney } from "@/domain/money/format"
import { useMonthView } from "@/features/plan/useMonthFigures"

const TOP_CATEGORIES = 5

/** FR-RPT-02: planned versus actual per bucket, the biggest categories, and the change from last month. */
export function ReportPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const [monthKey, setMonthKey] = useSelectedMonth(startDay)
  const month = useMonthView(monthKey, startDay)
  const previous = useMonthView(shiftMonth(monthKey, -1), startDay)
  const money = (v: number) => formatMoney(v, base, i18n.language)
  const signed = (v: number) => (v > 0 ? "+" : "") + money(v)

  const spent = (view: typeof month) => view.figures.buckets.reduce((s, b) => s + b.spent, 0)
  const categories = Object.entries(
    month.transactions.filter((tx) => tx.type === "EXPENSE").reduce<Record<string, number>>((acc, tx) => {
      const key = tx.category ?? t("report.uncategorised")
      acc[key] = (acc[key] ?? 0) + tx.baseAmount
      return acc
    }, {}),
  ).sort((a, b) => b[1] - a[1]).slice(0, TOP_CATEGORIES)
  const savingRate = month.income.received > 0 ? Math.round(((month.income.received - spent(month)) * 1000) / month.income.received) / 10 : null

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("report.title")}</h1>
        <div className="flex items-center gap-2">
          <MonthPicker value={monthKey} onChange={setMonthKey} />
          <span className="flex gap-2 print:hidden">
            <Button variant="neutral" size="sm" onClick={() => window.print()}><Printer /> {t("report.savePdf")}</Button>
            <Button variant="neutral" size="sm" asChild><Link to="/report/year">{t("report.year")}</Link></Button>
          </span>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("report.summary")}</CardTitle>
          <CardDescription>
            {t("income.received")}: {money(month.income.received)} · {t("plan.spent")}: {money(spent(month))}
            {savingRate !== null ? ` · ${t("report.savingRate", { rate: savingRate })}` : ""}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm">{t("report.monthOverMonth", { delta: signed(spent(month) - spent(previous)) })}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("report.plannedVsActual")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-2">
          {month.figures.buckets.map((figure) => {
            const bucket = month.buckets.find((b) => b.id === figure.id)!
            const actual = figure.spent + figure.transfersOut
            const ratio = figure.allocated > 0 ? Math.min(100, Math.round((actual * 100) / figure.allocated)) : 0
            return (
              <div key={figure.id} className="flex flex-col gap-1">
                <div className="flex justify-between text-sm">
                  <span className="flex items-center gap-2"><span className="size-3 rounded-base border-2 border-border" style={{ background: bucket.colour }} />{bucket.name}</span>
                  <span className="tabular-nums">{money(actual)} / {money(figure.allocated)}</span>
                </div>
                <div className="h-3 w-full overflow-hidden rounded-base border-2 border-border bg-secondary-background">
                  <div className={`h-full ${actual > figure.allocated ? "bg-chart-2" : "bg-main"}`} style={{ width: `${ratio}%` }} />
                </div>
              </div>
            )
          })}
          {month.figures.buckets.length === 0 && <p className="opacity-70">{t("plan.noBuckets")}</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("report.topCategories")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-1">
          {categories.length === 0 && <p className="opacity-70">{t("transactions.none")}</p>}
          {categories.map(([name, amount]) => (
            <div key={name} className="flex justify-between text-sm"><span>{name}</span><span className="tabular-nums">{money(amount)}</span></div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
