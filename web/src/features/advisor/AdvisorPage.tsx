import { useState } from "react"
import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { db } from "@/db/schema"
import { formatMonthKey } from "@/app/month"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { formatMoney } from "@/domain/money/format"

/** §4.2 P4: what an advisor sees, built by members' devices from shared records; never a transaction. */
export function AdvisorPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const [year, setYear] = useState(new Date().getFullYear())
  const report = useLiveQuery(async () => (await db.annualReports.where("year").equals(year).filter((r) => r.deletedAt === null).first())?.report ?? null, [year], null)
  const money = (v: number) => formatMoney(v, report?.baseCurrency ?? "IQD", i18n.language)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl">{t("advisor.title", { household: session?.householdName ?? "" })}</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setYear(year - 1)}>{year - 1}</Button>
          <Button variant="outline" size="sm" onClick={() => setYear(year + 1)}>{year + 1}</Button>
        </div>
      </div>
      <p className="text-sm opacity-70">{t("advisor.body")}</p>
      {!report && <p className="opacity-70">{t("advisor.noReport")}</p>}
      {report && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>{t("report.yearTitle", { year })}</CardTitle>
              <CardDescription>{t("report.savingRateDefinition")}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-3">
              <div><p className="text-xs opacity-70">{t("income.received")}</p><p className="font-semibold tabular-nums">{money(report.received ?? 0)}</p></div>
              <div><p className="text-xs opacity-70">{t("plan.spent")}</p><p className="font-semibold tabular-nums">{money(report.spent ?? 0)}</p></div>
              <div><p className="text-xs opacity-70">{t("report.rate")}</p><p className="font-semibold tabular-nums">{report.savingRate == null ? "—" : `${report.savingRate}%`}</p></div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>{t("report.months")}</CardTitle></CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead><tr className="opacity-70"><th className="whitespace-nowrap px-2 py-1 text-start">{t("report.month")}</th><th className="whitespace-nowrap px-2 text-end">{t("income.received")}</th><th className="whitespace-nowrap px-2 text-end">{t("plan.spent")}</th><th className="whitespace-nowrap px-2 text-end">{t("report.saved")}</th><th className="whitespace-nowrap px-2 text-end">{t("report.rate")}</th><th className="whitespace-nowrap px-2 text-end">{t("networth.title")}</th></tr></thead>
                <tbody>
                  {(report.months ?? []).map((m) => {
                    const snapshot = (report.snapshots ?? []).find((s) => s.monthKey === m.monthKey)
                    return (
                      <tr key={m.monthKey}>
                        <td className="whitespace-nowrap px-2 py-1">{formatMonthKey(m.monthKey ?? "", i18n.language)}</td>
                        <td className="whitespace-nowrap px-2 text-end tabular-nums">{money(m.received ?? 0)}</td>
                        <td className="whitespace-nowrap px-2 text-end tabular-nums">{money(m.spent ?? 0)}</td>
                        <td className="whitespace-nowrap px-2 text-end tabular-nums">{money(m.saved ?? 0)}</td>
                        <td className="whitespace-nowrap px-2 text-end tabular-nums">{m.savingRate == null ? "—" : `${m.savingRate}%`}</td>
                        <td className="whitespace-nowrap px-2 text-end tabular-nums">{snapshot ? money(snapshot.netWorth ?? 0) : "—"}</td>
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
