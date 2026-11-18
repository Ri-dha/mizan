import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { correctPrice, deletePriceChange } from "@/api/admin"
import { api, unwrap } from "@/api/client"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useAdminAction, useWhen } from "./adminAction"

const INSTRUMENTS = ["USDIQD_PARALLEL", "USDIQD_OFFICIAL", "XAU_LOCAL", "XAU_LOCAL_BID", "XAG_LOCAL", "XAG_LOCAL_BID", "XAU", "XAG"] as const
type Instrument = (typeof INSTRUMENTS)[number]
const MICROS = 1_000_000

interface Tick {
  id?: number
  observedAt?: string
  priceMicros?: number
  previousMicros?: number | null
  source?: string
}

/** Prices are entered as the stored unit (IQD or USD per dollar, gram or ounce), never per mithqal. */
export function PricesTab() {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const [instrument, setInstrument] = useState<Instrument>("USDIQD_PARALLEL")
  const [ticks, setTicks] = useState<Tick[]>([])
  const [price, setPrice] = useState("")
  const [reason, setReason] = useState("")

  const fetchTicks = useCallback(
    () => unwrap(api.GET("/api/v1/market/changes", { params: { query: { instrument, limit: 30 } } })).then((page) => page.changes ?? []),
    [instrument],
  )
  const load = async () => setTicks(await fetchTicks())
  useEffect(() => {
    fetchTicks().then(setTicks).catch(() => setTicks([]))
  }, [fetchTicks])

  async function submit() {
    const micros = Math.round(Number(price) * MICROS)
    if (!Number.isFinite(micros) || micros <= 0 || reason.trim() === "") return
    const ok = await run(async () => { await correctPrice(instrument, micros, reason.trim()); await load() }, t("admin.prices.corrected"))
    if (ok) { setPrice(""); setReason("") }
  }

  async function remove(id: number) {
    const why = window.prompt(t("admin.prices.deleteReason"))
    if (!why?.trim()) return
    await run(async () => { await deletePriceChange(id, why.trim()); await load() }, t("admin.prices.deleted"))
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.prices.title")}</CardTitle>
          <CardDescription>{t("admin.prices.body")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <Select value={instrument} onValueChange={(v) => setInstrument(v as Instrument)}>
            <SelectTrigger className="w-64" aria-label={t("admin.prices.instrument")}><SelectValue /></SelectTrigger>
            <SelectContent>{INSTRUMENTS.map((i) => <SelectItem key={i} value={i}>{t(`admin.instruments.${i}`)}</SelectItem>)}</SelectContent>
          </Select>
          <div className="grid gap-3 sm:grid-cols-[12rem_1fr_auto] sm:items-end">
            <Field id="correct-price" label={t("admin.prices.newPrice")}>
              <Input id="correct-price" dir="ltr" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} />
            </Field>
            <Field id="correct-reason" label={t("admin.reason")}>
              <Input id="correct-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
            </Field>
            <Button onClick={() => void submit()} disabled={!price || !reason.trim()}>{t("admin.prices.correct")}</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("admin.prices.recent")}</CardTitle></CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-muted-foreground">
                <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("prices.when")}</th>
                <th className="whitespace-nowrap px-2 py-1 text-end font-medium">{t("prices.price")}</th>
                <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("prices.source")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {ticks.map((tick) => (
                <tr key={tick.id} className="border-t">
                  <td className="whitespace-nowrap px-2 py-1.5">{when(tick.observedAt)}</td>
                  <td className="whitespace-nowrap px-2 py-1.5 text-end font-mono tabular-nums" dir="ltr">{((tick.priceMicros ?? 0) / MICROS).toLocaleString("en", { maximumFractionDigits: 2 })}</td>
                  <td className="whitespace-nowrap px-2 py-1.5 text-muted-foreground">{tick.source}</td>
                  <td className="px-2 py-1.5 text-end"><Button size="sm" variant="ghost" className="text-destructive" onClick={() => void remove(tick.id ?? 0)}>{t("admin.prices.delete")}</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  )
}
