import { useLiveQuery } from "dexie-react-hooks"
import { Plus } from "lucide-react"
import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { usePreferences } from "@/app/preferences"
import { Field } from "@/components/Field"
import { HelpButton } from "@/components/HelpButton"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { addValuation, currentValue, liveAssets, liveValuations, realisedGain, removeValuation, sellAsset, unsellAsset, valuationsOf } from "@/db/assets"
import type { Asset, AssetType } from "@/db/schema"
import { restore, softDelete } from "@/db/write"
import { todayIso } from "@/domain/calendar/month"
import { formatMoney, toMinorUnits } from "@/domain/money/format"
import { useScreenTour } from "@/tours/useTour"
import { useDateFormat } from "@/app/dates"
import { db } from "@/db/schema"
import { AssetSheet } from "./AssetSheet"

const TYPE_ORDER: AssetType[] = ["VEHICLE", "PROPERTY", "ELECTRONICS", "EQUIPMENT", "FURNITURE", "LIVESTOCK", "OTHER"]

type Action = { kind: "value" | "sell"; asset: Asset } | null

export function AssetsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const prefs = usePreferences()
  const assets = useLiveQuery(liveAssets, [], [])
  const valuations = useLiveQuery(liveValuations, [], [])
  const runningCosts = useLiveQuery(async () => {
    const rows = await db.transactions.filter((x) => x.deletedAt === null && x.type === "EXPENSE" && x.assetId !== null && x.assetId !== undefined).toArray()
    const totals: Record<string, number> = {}
    for (const row of rows) totals[row.assetId!] = (totals[row.assetId!] ?? 0) + row.baseAmount
    return totals
  }, [], {} as Record<string, number>)
  const [editing, setEditing] = useState<Asset | null | "new">(null)
  const [action, setAction] = useState<Action>(null)
  const [amount, setAmount] = useState("")
  const [on, setOn] = useState(todayIso())
  const [note, setNote] = useState("")
  const [showSold, setShowSold] = useState(false)
  const today = todayIso()
  useScreenTour("assets")

  const money = (v: number, currency = base) => formatMoney(v, currency, i18n.language)
  const date = useDateFormat()
  const held = assets.filter((a) => a.status === "HELD")
  const sold = assets.filter((a) => a.status === "SOLD")
  const totalBase = held.reduce((sum, a) => sum + currentValue(a, valuations, today).baseValue, 0)

  async function remove(asset: Asset) {
    await softDelete("asset", asset.id)
    setEditing(null)
    toast(t("assets.deleted"), { action: { label: t("accounts.restore"), onClick: () => void restore("asset", asset.id) } })
  }

  async function submitAction(event: FormEvent) {
    event.preventDefault()
    if (!action) return
    const value = toMinorUnits(amount, action.asset.currency)
    if (action.kind === "value") await addValuation(action.asset, on, value, note.trim() || null)
    else await sellAsset(action.asset.id, on, value)
    setAction(null)
    setAmount("")
    setNote("")
  }

  function openAction(kind: "value" | "sell", asset: Asset) {
    setAmount("")
    setOn(todayIso())
    setNote("")
    setAction({ kind, asset })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-3xl">{t("assets.title")}</h1>
        <div className="flex items-center gap-2">
          <HelpButton tour="assets" />
          <Button data-tour="assets-add" onClick={() => setEditing("new")}><Plus /> {t("assets.add")}</Button>
        </div>
      </div>

      <Card data-tour="assets-total">
        <CardHeader>
          <CardTitle>{t("assets.totalTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-1">
          <p className="text-3xl font-heading tabular-nums">{money(totalBase)}</p>
          <p className="text-sm opacity-70">{t("assets.totalBody", { count: held.length })}</p>
        </CardContent>
      </Card>

      {held.length === 0 && <p className="opacity-70">{t("assets.none")}</p>}

      {TYPE_ORDER.filter((type) => held.some((a) => a.type === type)).map((type) => (
        <section key={type} className="flex flex-col gap-2" data-tour={type === held[0]?.type ? "assets-list" : undefined}>
          <h2 className="text-xl">{t(`assets.types.${type}`)}</h2>
          {held.filter((a) => a.type === type).map((asset) => {
            const value = currentValue(asset, valuations, today)
            const history = valuationsOf(asset.id, valuations)
            const stale = value.monthsSinceBaseline >= prefs.valuationReminderMonths
            const change = value.value - asset.purchasePrice
            return (
              <Card key={asset.id}>
                <CardHeader className="flex-row items-start justify-between gap-2">
                  <div className="min-w-0">
                    <CardTitle className="cursor-pointer" onClick={() => setEditing(asset)}>{asset.name}</CardTitle>
                    <p className="text-sm opacity-70">
                      {Object.entries(asset.attributes).map(([k, v]) => `${t(`assets.attributes.${k}`)}: ${v}`).join(" · ")}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <span className="text-xl font-heading tabular-nums">{money(value.value, asset.currency)}</span>
                    <span className="flex gap-1">
                      {asset.liquidity === "ILLIQUID" && <Badge variant="neutral">{t("assets.illiquidBadge")}</Badge>}
                      {asset.visibility === "PRIVATE" && <Badge variant="neutral">{t("accounts.private")}</Badge>}
                      {stale && <Badge>{t("assets.staleBadge")}</Badge>}
                    </span>
                  </div>
                </CardHeader>
                <CardContent className="flex flex-col gap-3">
                  <p className="text-sm">
                    {t(value.baselineSource === "VALUATION" ? "assets.valueFromValuation" : "assets.valueFromPurchase", { date: date(value.baselineDate) })}
                    {asset.depreciationMethod !== "NONE" && ` · ${t("assets.depreciatedLine", { months: value.monthsSinceBaseline, rate: asset.annualRateBasisPoints / 100 })}`}
                  </p>
                  <p className={`text-sm tabular-nums ${change < 0 ? "text-chart-2" : ""}`}>
                    {t("assets.sincePurchase", { amount: (change > 0 ? "+" : "") + money(change, asset.currency) })}
                  </p>
                  {(runningCosts[asset.id] ?? 0) > 0 && (
                    <p className="text-sm tabular-nums">{t("assets.tco", { costs: money(runningCosts[asset.id]), total: money(asset.purchasePrice + runningCosts[asset.id] - value.value, asset.currency) })}</p>
                  )}
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" data-tour={asset.id === held[0]?.id ? "assets-value" : undefined} onClick={() => openAction("value", asset)}>{t("assets.addValuation")}</Button>
                    <Button size="sm" variant="neutral" onClick={() => openAction("sell", asset)}>{t("assets.sell")}</Button>
                  </div>
                  {history.slice(0, 3).map((v) => (
                    <div key={v.id} className="flex items-center justify-between gap-2 text-sm">
                      <span>{date(v.valuedOn)}{v.note ? ` · ${v.note}` : ""}</span>
                      <span className="flex items-center gap-2 tabular-nums">{money(v.value, asset.currency)}
                        <Button size="sm" variant="neutral" onClick={() => void removeValuation(v.id)}>{t("bills.undo")}</Button>
                      </span>
                    </div>
                  ))}
                </CardContent>
              </Card>
            )
          })}
        </section>
      ))}

      {sold.length > 0 && (
        <section className="flex flex-col gap-2">
          <Button variant="neutral" className="self-start" onClick={() => setShowSold((s) => !s)}>{t("assets.soldToggle", { count: sold.length })}</Button>
          {showSold && sold.map((asset) => {
            const gain = realisedGain(asset) ?? 0
            return (
              <Card key={asset.id}>
                <CardContent className="flex items-center justify-between gap-2 py-4">
                  <div className="flex flex-col">
                    <span className="font-heading">{asset.name}</span>
                    <span className="text-sm opacity-70">{t("assets.soldLine", { date: asset.soldOn ? date(asset.soldOn) : "", price: money(asset.salePrice ?? 0, asset.currency) })}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`tabular-nums ${gain < 0 ? "text-chart-2" : ""}`}>{(gain > 0 ? "+" : "") + money(gain, asset.currency)}</span>
                    <Button size="sm" variant="neutral" onClick={() => void unsellAsset(asset.id)}>{t("bills.undo")}</Button>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </section>
      )}

      <AssetSheet open={editing !== null} asset={editing === "new" ? null : editing} onClose={() => setEditing(null)} onDelete={remove} />

      <Dialog open={action !== null} onOpenChange={(open) => !open && setAction(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{action ? `${t(action.kind === "value" ? "assets.addValuation" : "assets.sell")}: ${action.asset.name}` : ""}</DialogTitle></DialogHeader>
          <form onSubmit={submitAction} className="flex flex-col gap-4">
            <Field id="amount" label={action?.kind === "sell" ? t("assets.salePrice") : t("assets.value")}>
              <Input id="amount" inputMode="decimal" dir="ltr" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
            </Field>
            <Field id="on" label={t("transactions.date")}>
              <Input id="on" type="date" dir="ltr" value={on} onChange={(e) => setOn(e.target.value)} required />
            </Field>
            {action?.kind === "value" && (
              <Field id="note" label={t("transactions.note")}>
                <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={120} />
              </Field>
            )}
            {action?.kind === "sell" && <p className="text-xs opacity-70">{t("assets.sellHint")}</p>}
            <DialogFooter><Button type="submit">{t("accounts.save")}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
