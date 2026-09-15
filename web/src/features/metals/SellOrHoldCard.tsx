import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import type { Metal } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { MICROGRAMS_PER_TROY_OUNCE } from "@/domain/metal/valuation"
import { dayRange, holdingsSeries, sellVerdict } from "@/domain/metal/series"
import { formatMoney } from "@/domain/money/format"
import { useHistory } from "@/market/history"
import type { MetalsView } from "./useMetals"

const CHART_DAYS = 180

/** FR-MTL-08 and FR-MTL-11 together: would selling today gain, and how has that answer moved. */
export function SellOrHoldCard({ view, currency }: { view: MetalsView; currency: string }) {
  const { t, i18n } = useTranslation()
  const [metal, setMetal] = useState<Metal>("GOLD")
  const history = useHistory(metal === "GOLD" ? ["XAU_LOCAL", "XAU_LOCAL_BID", "XAU", "USDIQD_PARALLEL"] : ["XAG_LOCAL", "XAG_LOCAL_BID", "XAG", "USDIQD_PARALLEL"])
  const money = (v: number) => formatMoney(v, currency, i18n.language)
  const open = view.valued.filter((v) => v.lot.metal === metal && v.remainingMg > 0)
  const lots = view.lots.filter((l) => l.metal === metal)
  const bidQuote = view.quotes.find((q) => q.instrument === (metal === "GOLD" ? "XAU_LOCAL_BID" : "XAG_LOCAL_BID"))
  const bidPerGram = bidQuote?.priceMicros ?? null

  const series = useMemo(() => {
    const local = history[metal === "GOLD" ? "XAU_LOCAL" : "XAG_LOCAL"] ?? {}
    const bid = history[metal === "GOLD" ? "XAU_LOCAL_BID" : "XAG_LOCAL_BID"] ?? {}
    const spot = history[metal === "GOLD" ? "XAU" : "XAG"] ?? {}
    const rate = history.USDIQD_PARALLEL ?? {}
    // Before the local feed existed, world spot × the dollar rate stands in for the ask.
    const ask: Record<string, number> = { ...local }
    for (const [day, oz] of Object.entries(spot)) {
      if (ask[day] === undefined && rate[day] !== undefined) ask[day] = Number((BigInt(oz) * BigInt(rate[day]) + MICROGRAMS_PER_TROY_OUNCE / 2n) / MICROGRAMS_PER_TROY_OUNCE)
    }
    const first = lots.map((l) => l.purchaseDate).sort()[0]
    const to = todayIso()
    const from = new Date(Date.parse(to) - CHART_DAYS * 86_400_000).toISOString().slice(0, 10)
    const start = first && first > from ? first : from
    return holdingsSeries(lots, view.disposals, view.disposalLots, dayRange(start, to), { ask, bid }).filter((p) => p.cost > 0 || p.valueAsk)
  }, [history, lots, metal, view.disposals, view.disposalLots])

  const verdict = sellVerdict(open, bidPerGram)
  const dateLabel = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { day: "numeric", month: "short" }).format(new Date(iso))

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-2">
        <div>
          <CardTitle>{t("metals.sellOrHold")}</CardTitle>
          <CardDescription>{t("metals.sellOrHoldBody")}</CardDescription>
        </div>
        <div className="flex gap-1">
          {(["GOLD", "SILVER"] as const).map((m) => (
            <Button key={m} size="sm" variant={metal === m ? "default" : "neutral"} onClick={() => setMetal(m)}>{t(`metals.metal.${m}`)}</Button>
          ))}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {open.length === 0 ? <p className="opacity-70">{t("metals.noLots")}</p> : bidPerGram === null ? <p className="opacity-70">{t("metals.noBid")}</p> : (
          <div className="flex flex-wrap items-baseline gap-3">
            <Badge variant={verdict.gain >= 0 ? "default" : "neutral"}>{verdict.gain >= 0 ? t("metals.verdictGain") : t("metals.verdictLoss")}</Badge>
            <span className={`text-2xl font-heading tabular-nums ${verdict.gain < 0 ? "text-chart-2" : ""}`}>{(verdict.gain > 0 ? "+" : "") + money(verdict.gain)}{verdict.gainPercent !== null ? ` (${verdict.gainPercent}%)` : ""}</span>
            <span className="text-sm opacity-70">{t("metals.verdictLine", { proceeds: money(verdict.proceeds), cost: money(verdict.cost), source: bidQuote?.source ?? "" })}</span>
          </div>
        )}
        {open.length > 0 && verdict.breakEvenPerGram24kMicros !== null && (
          <p className="text-sm opacity-70">{t("metals.breakEven", { price: money(Math.round(verdict.breakEvenPerGram24kMicros / 1e6)), bid: bidPerGram === null ? "—" : money(Math.round(bidPerGram / 1e6)) })}</p>
        )}
        {series.length > 1 && (
          <div dir="ltr" className="h-64 w-full">
            <ResponsiveContainer>
              <LineChart data={series.map((p) => ({ ...p, label: dateLabel(p.day) }))} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="4 4" opacity={0.3} />
                <XAxis dataKey="label" stroke="var(--foreground)" fontSize={12} minTickGap={24} />
                <YAxis stroke="var(--foreground)" fontSize={12} tickFormatter={(v: number) => new Intl.NumberFormat("en", { notation: "compact" }).format(v)} width={56} />
                <Tooltip formatter={(v, name) => [money(Number(v)), t(`metals.seriesNames.${String(name)}`)]} contentStyle={{ background: "var(--secondary-background)", border: "2px solid var(--border)", borderRadius: 5, color: "var(--foreground)" }} />
                <Line type="stepAfter" dataKey="cost" name="cost" stroke="var(--foreground)" strokeWidth={2} dot={false} strokeDasharray="6 3" />
                <Line type="monotone" dataKey="valueAsk" name="valueAsk" stroke="var(--main)" strokeWidth={3} dot={false} connectNulls />
                <Line type="monotone" dataKey="valueBid" name="valueBid" stroke="var(--chart-2)" strokeWidth={3} dot={false} connectNulls />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
        {series.length <= 1 && open.length > 0 && <p className="text-xs opacity-70">{t("metals.seriesGrows")}</p>}
      </CardContent>
    </Card>
  )
}
