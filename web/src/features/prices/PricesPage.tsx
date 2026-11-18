import { useState } from "react"
import { useTranslation } from "react-i18next"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"

import { usePreferences } from "@/app/preferences"
import { SourceCredit } from "@/components/SourceCredit"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { formatMoney } from "@/domain/money/format"
import { cn } from "@/lib/utils"
import { RANGES, SERIES, SERIES_KEYS, usePriceSeries, type Range, type SeriesKey } from "@/market/prices"

const SHORT_RANGES: ReadonlySet<Range> = new Set(["1D", "1W"])
const COMPACT_FROM = 100_000

/** The dollar moves a few dinars at a time, so its axis needs whole numbers; gold per mithqal does not. */
function axisNumber(value: number): string {
  return new Intl.NumberFormat("en", value >= COMPACT_FROM ? { notation: "compact", maximumFractionDigits: 2 } : { maximumFractionDigits: 0 }).format(value)
}

/** T-price-history: every recorded change of the dollar, gold and silver, for anyone signed in. */
export function PricesPage() {
  const { t, i18n } = useTranslation()
  usePreferences()
  const [key, setKey] = useState<SeriesKey>("usdMarket")
  const [range, setRange] = useState<Range>("1M")
  const series = usePriceSeries(key, range)
  const official = usePriceSeries("usdOfficial", "1D")
  const usd = SERIES[key].unit === "usdPerOunce"
  const locale = i18n.language === "ar" ? "ar-IQ" : "en-GB"

  const format = (value: number) => (usd ? formatMoney(Math.round(value * 100), "USD", i18n.language) : formatMoney(Math.round(value), "IQD", i18n.language))
  const formatSigned = (value: number) => `${value > 0 ? "+" : value < 0 ? "−" : ""}${format(Math.abs(value))}`
  const percent = (value: number, base: number) => (base === 0 ? "" : `${value > 0 ? "+" : value < 0 ? "−" : ""}${Math.abs((value / base) * 100).toFixed(2)}%`)
  const when = (iso: string, withTime: boolean) =>
    new Intl.DateTimeFormat(locale, withTime ? { dateStyle: "medium", timeStyle: "short" } : { dateStyle: "medium" }).format(new Date(iso))

  const latest = series.changes[0]
  const first = series.points[0]
  const moved = latest && first ? latest.value - first.value : null
  const officialNow = official.changes[0]?.value
  const gap = key === "usdMarket" && latest && officialNow ? ((latest.value - officialNow) / officialNow) * 100 : null

  const config = { value: { label: t(`prices.series.${key}`), color: "var(--chart-1)" } } satisfies ChartConfig
  const data = series.points.map((p) => ({ at: p.at, value: p.value }))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl">{t("prices.title")}</h1>
        <Select value={key} onValueChange={(value) => setKey(value as SeriesKey)}>
          <SelectTrigger className="w-60" aria-label={t("prices.pick")}><SelectValue /></SelectTrigger>
          <SelectContent>
            {SERIES_KEYS.map((k) => <SelectItem key={k} value={k}>{t(`prices.series.${k}`)}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardHeader>
          <CardDescription>{t(`prices.series.${key}`)} · {t(`prices.units.${SERIES[key].unit}`)}</CardDescription>
          <CardTitle className="text-4xl tabular-nums">{latest ? format(latest.value) : "—"}</CardTitle>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            {moved !== null && first && (
              <span className={cn("tabular-nums", moved > 0 && "text-success", moved < 0 && "text-destructive")}>
                {formatSigned(moved)} ({percent(moved, first.value)}) {t(`prices.since.${range}`)}
              </span>
            )}
            {gap !== null && <span className="text-muted-foreground">{t("prices.gap", { percent: gap.toFixed(1) })}</span>}
          </div>
          {latest && (
            <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              {t("prices.updated", { when: when(latest.at, true), source: latest.source })}
              <SourceCredit source={latest.source} />
            </p>
          )}
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <ToggleGroup type="single" variant="outline" size="sm" className="w-full sm:w-auto" value={range} onValueChange={(value) => value && setRange(value as Range)} aria-label={t("prices.range")}>
            {RANGES.map((r) => <ToggleGroupItem key={r} value={r} className="flex-1 px-1 text-xs sm:px-3 sm:text-sm">{t(`prices.ranges.${r}`)}</ToggleGroupItem>)}
          </ToggleGroup>
          {data.length < 2 ? (
            <p className="py-12 text-center text-sm text-muted-foreground">{series.loading ? t("common.loading") : t("prices.notEnough")}</p>
          ) : (
            <div dir="ltr">
              <ChartContainer config={config} className="aspect-auto h-72 w-full">
                <LineChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="at" tickLine={false} axisLine={false} minTickGap={32}
                    tickFormatter={(v: string) => new Intl.DateTimeFormat(locale, SHORT_RANGES.has(range) ? { day: "numeric", hour: "2-digit" } : { month: "short", day: "numeric" }).format(new Date(v))} />
                  <YAxis tickLine={false} axisLine={false} width={64} domain={["auto", "auto"]}
                    tickFormatter={axisNumber} />
                  <ChartTooltip content={<ChartTooltipContent labelFormatter={(_, payload) => when(String(payload?.[0]?.payload?.at ?? ""), SHORT_RANGES.has(range))}
                    formatter={(value) => <span className="font-mono tabular-nums">{format(Number(value))}</span>} />} />
                  <Line dataKey="value" type={SHORT_RANGES.has(range) ? "stepAfter" : "monotone"} stroke="var(--color-value)" strokeWidth={2} dot={false} />
                </LineChart>
              </ChartContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("prices.changes")}</CardTitle>
          <CardDescription>{t("prices.changesBody")}</CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          {series.changes.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("prices.noChanges")}</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground">
                  <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("prices.when")}</th>
                  <th className="whitespace-nowrap px-2 py-1 text-end font-medium">{t("prices.price")}</th>
                  <th className="whitespace-nowrap px-2 py-1 text-end font-medium">{t("prices.change")}</th>
                  <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("prices.source")}</th>
                </tr>
              </thead>
              <tbody>
                {series.changes.map((c) => {
                  const delta = c.previous === null ? null : c.value - c.previous
                  return (
                    <tr key={c.id} className="border-t">
                      <td className="whitespace-nowrap px-2 py-1.5">{when(c.at, true)}</td>
                      <td className="whitespace-nowrap px-2 py-1.5 text-end tabular-nums">{format(c.value)}</td>
                      <td className={cn("whitespace-nowrap px-2 py-1.5 text-end tabular-nums", delta !== null && delta > 0 && "text-success", delta !== null && delta < 0 && "text-destructive")}>
                        {delta === null ? t("prices.firstPrice") : `${formatSigned(delta)} (${percent(delta, c.previous!)})`}
                      </td>
                      <td className="whitespace-nowrap px-2 py-1.5 text-muted-foreground">{c.source}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
