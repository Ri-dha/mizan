import { useLiveQuery } from "dexie-react-hooks"
import { Printer } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"

import { useSession } from "@/api/auth"
import { currentMonthKey, formatMonthKey } from "@/app/month"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { trailingSavingRate, yearSummary, type YearSummary } from "@/db/report"
import { formatMoney } from "@/domain/money/format"

const PALETTE = ["#88aaee", "#ffdc58", "#ff6b6b", "#a3e635", "#c4a1ff", "#fd9745", "#7fdbca", "#f472b6"]
const TOP_CATEGORIES = 8

/** FR-RPT-03/04/05: the year at a glance, spending trends per bucket and category, saving rate. */
export function YearReportPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const thisMonth = currentMonthKey(startDay)
  const [year, setYear] = useState(Number(thisMonth.slice(0, 4)))
  const uncategorised = t("report.uncategorised")
  const summary = useLiveQuery(() => yearSummary(year, startDay, uncategorised), [year, startDay, uncategorised], undefined as YearSummary | undefined)
  const trailing = useLiveQuery(() => trailingSavingRate(thisMonth, startDay), [thisMonth, startDay], null)
  const money = (v: number) => formatMoney(v, base, i18n.language)
  const monthLabel = (key: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { month: "short" }).format(new Date(Date.UTC(year, Number(key.slice(5, 7)) - 1, 1)))

  const bucketIds = summary ? [...new Set(summary.months.flatMap((m) => Object.keys(m.byBucket)))] : []
  const bucketName = (id: string) => summary?.months.map((m) => m.bucketNames[id]).find(Boolean) ?? id
  const chart = summary?.months.map((m) => ({ name: monthLabel(m.key), ...Object.fromEntries(bucketIds.map((id) => [id, m.byBucket[id] ?? 0])) })) ?? []
  const categories = summary ? Object.entries(summary.byCategory).sort((a, b) => b[1] - a[1]).slice(0, TOP_CATEGORIES) : []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("report.yearTitle", { year })}</h1>
        <div className="flex items-center gap-2 print:hidden">
          <Button variant="neutral" size="sm" onClick={() => setYear(year - 1)}>{year - 1}</Button>
          <Button variant="neutral" size="sm" onClick={() => setYear(year + 1)}>{year + 1}</Button>
          <Button variant="neutral" size="sm" onClick={() => window.print()}><Printer /> {t("report.savePdf")}</Button>
          <Button variant="neutral" size="sm" asChild><Link to="/report">{t("report.title")}</Link></Button>
        </div>
      </div>

      {summary && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>{t("report.summary")}</CardTitle>
              <CardDescription>{t("report.savingRateDefinition")}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Stat label={t("income.received")} value={money(summary.received)} />
              <Stat label={t("plan.spent")} value={money(summary.spent)} />
              <Stat label={t("report.saved")} value={money(summary.saved)} hint={summary.savingRate === null ? "" : t("report.savingRate", { rate: summary.savingRate })} />
              <Stat label={t("report.trailing")} value={trailing === null ? t("report.noProjection") : `${trailing}%`} />
              <Stat label={t("report.netWorthChange")} value={summary.netWorthStart !== null && summary.netWorthEnd !== null ? money(summary.netWorthEnd - summary.netWorthStart) : t("report.noSnapshots")}
                hint={summary.netWorthStart !== null && summary.netWorthEnd !== null ? `${money(summary.netWorthStart)} → ${money(summary.netWorthEnd)}` : ""} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>{t("report.spendingByBucket")}</CardTitle></CardHeader>
            <CardContent>
              <div dir="ltr" className="h-72 w-full">
                <ResponsiveContainer>
                  <BarChart data={chart} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="4 4" opacity={0.3} />
                    <XAxis dataKey="name" stroke="var(--foreground)" fontSize={12} />
                    <YAxis stroke="var(--foreground)" fontSize={12} tickFormatter={(v: number) => new Intl.NumberFormat("en", { notation: "compact" }).format(v)} width={56} />
                    <Tooltip formatter={(v, name) => [money(Number(v)), bucketName(String(name))]} contentStyle={{ background: "var(--secondary-background)", border: "2px solid var(--border)", borderRadius: 5, color: "var(--foreground)" }} />
                    <Legend formatter={(id) => bucketName(String(id))} />
                    {bucketIds.map((id, i) => <Bar key={id} dataKey={id} stackId="spent" fill={PALETTE[i % PALETTE.length]} stroke="var(--border)" />)}
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>{t("report.months")}</CardTitle></CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead><tr className="text-start opacity-70"><th className="py-1 text-start">{t("report.month")}</th><th className="text-end">{t("income.received")}</th><th className="text-end">{t("plan.spent")}</th><th className="text-end">{t("report.saved")}</th><th className="text-end">{t("report.rate")}</th></tr></thead>
                <tbody>
                  {summary.months.map((m) => (
                    <tr key={m.key} className={m.key === thisMonth ? "font-heading" : ""}>
                      <td className="py-1">{formatMonthKey(m.key, i18n.language)}</td>
                      <td className="text-end tabular-nums">{money(m.received)}</td>
                      <td className="text-end tabular-nums">{money(m.spent)}</td>
                      <td className={`text-end tabular-nums ${m.saved < 0 ? "text-chart-2" : ""}`}>{money(m.saved)}</td>
                      <td className="text-end tabular-nums">{m.savingRate === null ? "—" : `${m.savingRate}%`}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
        </>
      )}
    </div>
  )
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-base border-2 border-border p-3">
      <p className="text-xs opacity-70">{label}</p>
      <p className="font-heading tabular-nums">{value}</p>
      {hint && <p className="text-xs opacity-70 tabular-nums">{hint}</p>}
    </div>
  )
}
