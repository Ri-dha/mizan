import { Plus, Settings2 } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { realisedGain, undoSale } from "@/db/metals"
import type { Metal, MetalLot } from "@/db/schema"
import { formatMoney } from "@/domain/money/format"
import type { PriceSource } from "@/market/store"
import { LotSheet } from "./LotSheet"
import { MarketSettingsDialog } from "./MarketSettingsDialog"
import { OverrideSheet } from "./OverrideSheet"
import { PriceExplainer } from "./PriceExplainer"
import { SellSheet } from "./SellSheet"
import { useMetals } from "./useMetals"

export function grams(mg: number, locale: string): string {
  return `${new Intl.NumberFormat(locale === "ar" ? "ar-IQ" : "en-GB", { maximumFractionDigits: 3 }).format(mg / 1000)} g`
}

export function MetalsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const view = useMetals()
  const [editing, setEditing] = useState<MetalLot | null | "new">(null)
  const [selling, setSelling] = useState<Metal | null>(null)
  const [overriding, setOverriding] = useState(false)
  const [settings, setSettings] = useState(false)
  const [explaining, setExplaining] = useState(false)

  const money = (v: number) => formatMoney(v, base, i18n.language)
  const signed = (v: number) => (v > 0 ? "+" : "") + money(v)
  const openLots = view.valued.filter((v) => v.remainingMg > 0)
  const totals = view.lines.reduce((acc, l) => ({ cost: acc.cost + l.costBasis, value: acc.value + l.valueNow, gain: acc.gain + l.gain, gainX: acc.gainX + l.gainExcludingMaking }), { cost: 0, value: 0, gain: 0, gainX: 0 })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl">{t("metals.title")}</h1>
        <div className="flex gap-2">
          <Button variant="neutral" size="icon" aria-label={t("metals.settings")} onClick={() => setSettings(true)}><Settings2 /></Button>
          <Button onClick={() => setEditing("new")}><Plus /> {t("metals.addLot")}</Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("metals.portfolio")}</CardTitle>
          <CardDescription>
            {t("metals.costBasis")}: {money(totals.cost)} · {t("metals.valueNow")}: {money(totals.value)}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-wrap items-baseline gap-3">
            <span className={`text-3xl font-heading tabular-nums ${totals.gain < 0 ? "text-chart-2" : ""}`}>{signed(totals.gain)}</span>
            <span className="text-sm opacity-70">{t("metals.excludingMaking", { amount: signed(totals.gainX) })}</span>
          </div>
          {view.lines.length === 0 && <p className="opacity-70">{t("metals.noLots")}</p>}
          {view.lines.map((line) => (
            <div key={`${line.metal}-${line.purityLabel}`} className="grid grid-cols-2 gap-1 rounded-base border-2 border-border p-3 sm:grid-cols-5">
              <span className="col-span-2 font-heading sm:col-span-1">{t(`metals.metal.${line.metal}`)} {line.purityLabel}</span>
              <Fig label={t("metals.weight")} value={grams(line.weightMg, i18n.language)} />
              <Fig label={t("metals.costBasis")} value={money(line.costBasis)} />
              <Fig label={t("metals.valueNow")} value={money(line.valueNow)} />
              <Fig label={t("metals.gain")} value={signed(line.gain)} strong />
            </div>
          ))}
          <div className="flex flex-wrap gap-2">
            {(["GOLD", "SILVER"] as const).map((metal) => (
              <Button key={metal} variant="neutral" size="sm" disabled={!openLots.some((v) => v.lot.metal === metal)} onClick={() => setSelling(metal)}>
                {t("metals.sell", { metal: t(`metals.metal.${metal}`) })}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>{t("metals.prices")}</CardTitle>
          <div className="flex gap-2">
            <Button size="sm" variant="neutral" onClick={() => setExplaining(true)}>{t("metals.howCalculated")}</Button>
            <Button size="sm" variant="neutral" onClick={() => setOverriding(true)}>{t("metals.enterPrice")}</Button>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-2 text-sm">
          <PriceLine label={t("metals.metal.GOLD")} source={view.prices.spot.GOLD.source} value={view.prices.spot.GOLD.perGram24kOverrideMicros !== null ? `${money(Math.round(view.prices.spot.GOLD.perGram24kOverrideMicros / 1e6))}/g 24k` : `$${(view.prices.spot.GOLD.spotUsdPerOzMicros / 1e6).toFixed(2)}/oz`} />
          <PriceLine label={t("metals.metal.SILVER")} source={view.prices.spot.SILVER.source} value={view.prices.spot.SILVER.perGram24kOverrideMicros !== null ? `${money(Math.round(view.prices.spot.SILVER.perGram24kOverrideMicros / 1e6))}/g` : `$${(view.prices.spot.SILVER.spotUsdPerOzMicros / 1e6).toFixed(2)}/oz`} />
          <PriceLine label={`USD → ${base} (${t(`metals.rateKinds.${view.prices.rateKind}`)})`} source={view.prices.rateSource} value={money(Math.round(view.prices.usdIqdMicros / 1e6))} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("metals.lots")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-2">
          {openLots.length === 0 && <p className="opacity-70">{t("metals.noLots")}</p>}
          {openLots.map((v) => (
            <div key={v.lot.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => setEditing(v.lot)}>
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-heading">
                  {t(`metals.metal.${v.lot.metal}`)} {v.lot.purityLabel} · {grams(v.remainingMg, i18n.language)} · {t(`metals.forms.${v.lot.form}`)}
                </span>
                <span className="truncate text-sm opacity-70">
                  {v.lot.purchaseDate}{v.lot.dealer ? ` · ${v.lot.dealer}` : ""} · {t("metals.perGram", { cost: money(v.costPerGramBase), now: money(v.perGramNowBase) })}
                </span>
              </div>
              <div className="flex shrink-0 flex-col items-end">
                <span className="tabular-nums font-heading">{money(v.valueNow)}</span>
                <span className={`text-sm tabular-nums ${v.gain < 0 ? "text-chart-2" : ""}`}>{signed(v.gain)}{v.gainPercent !== null ? ` (${v.gainPercent}%)` : ""}</span>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {view.disposals.length > 0 && (
        <Card>
          <CardHeader><CardTitle>{t("metals.sales")}</CardTitle></CardHeader>
          <CardContent className="flex flex-col gap-2">
            {view.disposals.map((d) => {
              const r = realisedGain(d, view.disposalLots)
              return (
                <div key={d.id} className="flex items-center justify-between gap-3 rounded-base border-2 border-border p-3">
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate font-heading">{d.soldOn} · {t(`metals.metal.${d.metal}`)} {grams(d.weightMg, i18n.language)}</span>
                    <span className="text-sm opacity-70">{t(`metals.methods.${d.method}`)} · {t("metals.excludingMaking", { amount: signed(r.gainExcludingMaking) })}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`tabular-nums font-heading ${r.gain < 0 ? "text-chart-2" : ""}`}>{signed(r.gain)}</span>
                    <Button size="sm" variant="neutral" onClick={() => void undoSale(d, view.disposalLots)}>{t("bills.undo")}</Button>
                  </div>
                </div>
              )
            })}
          </CardContent>
        </Card>
      )}

      <LotSheet open={editing !== null} lot={editing === "new" ? null : editing} onClose={() => setEditing(null)} />
      <SellSheet metal={selling} view={view} onClose={() => setSelling(null)} />
      <OverrideSheet open={overriding} view={view} onClose={() => setOverriding(false)} />
      <MarketSettingsDialog open={settings} setting={view.setting} onClose={() => setSettings(false)} />
      <PriceExplainer open={explaining} view={view} onClose={() => setExplaining(false)} />
    </div>
  )
}

function Fig({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex flex-col">
      <span className="text-xs opacity-70">{label}</span>
      <span className={`tabular-nums ${strong ? "font-heading" : ""}`}>{value}</span>
    </div>
  )
}

function PriceLine({ label, value, source }: { label: string; value: string; source: PriceSource }) {
  const { t, i18n } = useTranslation()
  const when = source.at ? new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: source.kind === "feed" ? "short" : undefined }).format(new Date(source.at)) : ""
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span>{label}: <span className="font-heading tabular-nums">{value}</span></span>
      <span className="flex items-center gap-1">
        <Badge variant="neutral">{source.kind === "override" ? t("metals.sources.override") : source.kind === "feed" ? source.label : t("metals.sources.none")}</Badge>
        {source.stale && <Badge>{t("metals.stale")}</Badge>}
        <span className="text-xs opacity-70">{when}</span>
      </span>
    </div>
  )
}
