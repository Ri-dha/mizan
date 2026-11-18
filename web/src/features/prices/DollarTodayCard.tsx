import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { SourceCredit } from "@/components/SourceCredit"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { formatMoney } from "@/domain/money/format"
import { cn } from "@/lib/utils"
import { usePriceSeries } from "@/market/prices"

/** The market dollar today and its move since yesterday's close; hidden until a price is known. */
export function DollarTodayCard() {
  const { t, i18n } = useTranslation()
  const series = usePriceSeries("usdMarket", "1M")
  const latest = series.changes[0]
  if (!latest) return null
  const today = new Date().toISOString().slice(0, 10)
  const yesterday = [...series.points].reverse().find((p) => p.at < today)?.value ?? null
  const move = yesterday === null ? null : latest.value - yesterday
  const money = (v: number) => formatMoney(Math.round(v), "IQD", i18n.language)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("prices.dollarToday")}</CardTitle>
        <CardDescription className="flex flex-wrap items-center gap-2">
          {t("prices.series.usdMarket")}
          <SourceCredit source={latest.source} />
        </CardDescription>
      </CardHeader>
      <CardContent className="flex items-center justify-between gap-2">
        <div className="flex items-baseline gap-3">
          <span className="text-3xl font-semibold tabular-nums">{money(latest.value)}</span>
          {move !== null && move !== 0 && (
            <span className={cn("text-sm tabular-nums", move > 0 ? "text-success" : "text-destructive")}>
              {move > 0 ? "+" : "−"}{money(Math.abs(move))} {t("prices.sinceYesterday")}
            </span>
          )}
        </div>
        <Button asChild variant="outline"><Link to="/prices">{t("prices.open")}</Link></Button>
      </CardContent>
    </Card>
  )
}
