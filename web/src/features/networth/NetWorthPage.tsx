import { useState } from "react"
import { useTranslation } from "react-i18next"
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { api, unwrap } from "@/api/client"
import { describeError } from "@/app/errors"
import { currentMonthKey, formatMonthKey, shiftMonth } from "@/app/month"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { isMonthClosed, snapshotFor, trendPoints } from "@/db/networth"
import { formatMoney } from "@/domain/money/format"
import { syncNow } from "@/sync/engine"
import { usePreferences } from "@/app/preferences"
import { useNetWorth } from "./useNetWorth"

const MONTHS_LISTED = 12

export function NetWorthPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const view = useNetWorth()
  const [busy, setBusy] = useState<string | null>(null)
  const prefs = usePreferences()
  const usdIqd = Number(view.rateSet.usdIqdMicros) / 1e6
  const inUsd = (v: number) => formatMoney(Math.round((v / usdIqd) * 100), "USD", i18n.language)
  const money = (v: number) => formatMoney(v, base, i18n.language)

  const thisMonth = currentMonthKey(startDay)
  const months = Array.from({ length: MONTHS_LISTED }, (_, i) => shiftMonth(thisMonth, -i))
  const trend = [
    ...trendPoints(view.closes, view.snapshots).map((s) => ({ key: s.monthKey, label: formatMonthKey(s.monthKey, i18n.language), value: s.netWorth })),
    { key: "now", label: t("networth.now"), value: view.current.netWorth },
  ]

  async function act(monthKey: string, action: "close" | "reopen") {
    setBusy(monthKey)
    try {
      await unwrap(api.POST(action === "close" ? "/api/v1/months/{monthKey}/close" : "/api/v1/months/{monthKey}/reopen", { params: { path: { monthKey } } }))
      await syncNow()
      toast(t(action === "close" ? "networth.closed" : "networth.reopened", { month: formatMonthKey(monthKey, i18n.language) }))
    } catch (e) {
      toast(describeError(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl">{t("networth.title")}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{t("networth.headline")}</CardTitle>
          <CardDescription>{t("networth.formula")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className={`text-4xl font-heading tabular-nums ${view.current.netWorth < 0 ? "text-chart-2" : ""}`}>{money(view.current.netWorth)}</p>
          {prefs.showUsd && usdIqd > 0 && <p className="text-lg opacity-70 tabular-nums">≈ {inUsd(view.current.netWorth)} <span className="text-xs">({t(`metals.rateKinds.${String(view.rateSet.rateKind)}`)})</span></p>}
          <div className="grid gap-2 sm:grid-cols-2">
            <div className="rounded-base border-2 border-border p-3">
              <p className="text-sm opacity-70">{t("networth.assets")}</p>
              <p className="font-heading tabular-nums">{money(view.current.totalAssets)}</p>
              {view.current.composition.map((share) => (
                <div key={share.assetClass} className="flex justify-between text-sm">
                  <span>{t(`networth.classes.${share.assetClass}`)} <span className="opacity-70">{share.percent}%</span></span>
                  <span className="tabular-nums">{money(share.amount)}</span>
                </div>
              ))}
            </div>
            <div className="rounded-base border-2 border-border p-3">
              <p className="text-sm opacity-70">{t("networth.liabilities")}</p>
              <p className="font-heading tabular-nums">{money(view.current.totalLiabilities)}</p>
              <p className="mt-2 text-xs opacity-70">
                {t("networth.ratesInUse", { rate: money(Math.round(Number(view.rateSet.usdIqdMicros) / 1e6)), kind: t(`metals.rateKinds.${String(view.rateSet.rateKind)}`), gold: String(view.rateSet.xauSource) })}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("networth.trend")}</CardTitle>
          <CardDescription>{t("networth.trendBody")}</CardDescription>
        </CardHeader>
        <CardContent>
          <div dir="ltr" className="h-64 w-full">
            <ResponsiveContainer>
              <LineChart data={trend} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="4 4" opacity={0.3} />
                <XAxis dataKey="label" stroke="var(--foreground)" fontSize={12} />
                <YAxis stroke="var(--foreground)" fontSize={12} tickFormatter={(v: number) => new Intl.NumberFormat("en", { notation: "compact" }).format(v)} width={56} />
                <Tooltip formatter={(v) => money(Number(v))} contentStyle={{ background: "var(--secondary-background)", border: "2px solid var(--border)", borderRadius: 5, color: "var(--foreground)" }} />
                <Line type="monotone" dataKey="value" stroke="var(--main)" strokeWidth={3} dot={{ r: 5, fill: "var(--main)", stroke: "var(--border)", strokeWidth: 2 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("networth.months")}</CardTitle>
          <CardDescription>{t("networth.monthsBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {months.map((key) => {
            const closed = isMonthClosed(key, view.closes)
            const snapshot = snapshotFor(key, view.closes, view.snapshots)
            return (
              <div key={key} className="flex flex-wrap items-center justify-between gap-2 rounded-base border-2 border-border p-3">
                <div className="flex min-w-0 flex-col">
                  <span className="font-heading">{formatMonthKey(key, i18n.language)} {closed && <Badge variant="neutral">{t("networth.closedBadge")}</Badge>}</span>
                  {snapshot && closed && (
                    <span className="text-sm opacity-70">
                      {t("networth.snapshotLine", { value: money(snapshot.netWorth), at: new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(snapshot.takenAt)), rate: money(Math.round(Number(snapshot.rateSet.usdIqdMicros ?? 0) / 1e6)) })}
                    </span>
                  )}
                </div>
                <Button size="sm" variant={closed ? "neutral" : "default"} disabled={busy === key || !navigator.onLine} onClick={() => void act(key, closed ? "reopen" : "close")}>
                  {closed ? t("networth.reopen") : t("networth.close")}
                </Button>
              </div>
            )
          })}
          {!navigator.onLine && <p className="text-xs opacity-70">{t("networth.needsOnline")}</p>}
        </CardContent>
      </Card>
    </div>
  )
}
