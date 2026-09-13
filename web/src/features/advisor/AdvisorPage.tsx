import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { api, unwrap } from "@/api/client"
import { useSession } from "@/api/auth"
import { describeError } from "@/app/errors"
import { formatMonthKey } from "@/app/month"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { formatMoney } from "@/domain/money/format"

type Annual = Awaited<ReturnType<typeof fetchAnnual>>
const fetchAnnual = (year: number) => unwrap(api.GET("/api/v1/reports/annual", { params: { query: { year } } }))

/** §4.2 P4: what an advisor sees, computed on the server from shared records; never a transaction. */
export function AdvisorPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const [year, setYear] = useState(new Date().getFullYear())
  const [report, setReport] = useState<Annual | null>(null)
  const [error, setError] = useState<string | null>(null)
  const money = (v: number) => formatMoney(v, report?.baseCurrency ?? "IQD", i18n.language)

  useEffect(() => {
    fetchAnnual(year).then((r) => { setReport(r); setError(null) }).catch((e) => setError(describeError(e)))
  }, [year])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl">{t("advisor.title", { household: session?.householdName ?? "" })}</h1>
        <div className="flex gap-2">
          <Button variant="neutral" size="sm" onClick={() => setYear(year - 1)}>{year - 1}</Button>
          <Button variant="neutral" size="sm" onClick={() => setYear(year + 1)}>{year + 1}</Button>
        </div>
      </div>
      <p className="text-sm opacity-70">{t("advisor.body")}</p>
      {error && <p className="text-chart-2">{error}</p>}
      {report && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>{t("report.yearTitle", { year })}</CardTitle>
              <CardDescription>{t("report.savingRateDefinition")}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-3">
              <div><p className="text-xs opacity-70">{t("income.received")}</p><p className="font-heading tabular-nums">{money(report.received ?? 0)}</p></div>
              <div><p className="text-xs opacity-70">{t("plan.spent")}</p><p className="font-heading tabular-nums">{money(report.spent ?? 0)}</p></div>
              <div><p className="text-xs opacity-70">{t("report.rate")}</p><p className="font-heading tabular-nums">{report.savingRate == null ? "—" : `${report.savingRate}%`}</p></div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>{t("report.months")}</CardTitle></CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead><tr className="opacity-70"><th className="py-1 text-start">{t("report.month")}</th><th className="text-end">{t("income.received")}</th><th className="text-end">{t("plan.spent")}</th><th className="text-end">{t("report.saved")}</th><th className="text-end">{t("report.rate")}</th><th className="text-end">{t("networth.title")}</th></tr></thead>
                <tbody>
                  {(report.months ?? []).map((m) => {
                    const snapshot = (report.snapshots ?? []).find((s) => s.monthKey === m.monthKey)
                    return (
                      <tr key={m.monthKey}>
                        <td className="py-1">{formatMonthKey(m.monthKey ?? "", i18n.language)}</td>
                        <td className="text-end tabular-nums">{money(m.received ?? 0)}</td>
                        <td className="text-end tabular-nums">{money(m.spent ?? 0)}</td>
                        <td className="text-end tabular-nums">{money(m.saved ?? 0)}</td>
                        <td className="text-end tabular-nums">{m.savingRate == null ? "—" : `${m.savingRate}%`}</td>
                        <td className="text-end tabular-nums">{snapshot ? money(snapshot.netWorth ?? 0) : "—"}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
