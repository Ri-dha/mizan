import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import type { MarketInstrument } from "@/db/schema"
import { PURITIES, MILLIGRAMS_PER_UNIT } from "@/domain/metal/valuation"
import { setOverride } from "@/market/store"
import type { MetalsView } from "./useMetals"

/** People know the price of a mithqal of 21k, not a gram of pure gold; the form converts. */
export function OverrideSheet({ open, view, onClose }: { open: boolean; view: MetalsView; onClose: () => void }) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [instrument, setInstrument] = useState<MarketInstrument>("XAU")
  const [purity, setPurity] = useState("21k")
  const [unit, setUnit] = useState("MITHQAL")
  const [price, setPrice] = useState("")
  const [note, setNote] = useState("")

  const perGram24kMicros = instrument === "USDIQD"
    ? Math.round(Number(price) * 1e6)
    : Math.round(((Number(price) * 1e6) / (MILLIGRAMS_PER_UNIT[unit] / 1000)) * (10_000 / PURITIES[purity].basisPoints))

  async function submit(event: FormEvent) {
    event.preventDefault()
    await setOverride(instrument, perGram24kMicros, note.trim() || null)
    toast(t("metals.priceSaved"))
    onClose()
  }

  const purities = Object.values(PURITIES).filter((p) => p.metal === (instrument === "XAG" ? "SILVER" : "GOLD"))

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{t("metals.enterPrice")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <p className="text-sm opacity-70">{t("metals.overrideBody")}</p>
          <Field id="instrument" label={t("metals.priceOf")}>
            <Select value={instrument} onValueChange={(v) => { setInstrument(v as MarketInstrument); setPurity(v === "XAG" ? "999" : "21k") }}>
              <SelectTrigger id="instrument"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="XAU">{t("metals.metal.GOLD")}</SelectItem>
                <SelectItem value="XAG">{t("metals.metal.SILVER")}</SelectItem>
                <SelectItem value="USDIQD">USD → {base}</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          {instrument !== "USDIQD" && (
            <div className="grid grid-cols-2 gap-2">
              <Field id="purity" label={t("metals.purity")}>
                <Select value={purity} onValueChange={setPurity}>
                  <SelectTrigger id="purity"><SelectValue /></SelectTrigger>
                  <SelectContent>{purities.map((p) => <SelectItem key={p.label} value={p.label}>{p.label}</SelectItem>)}</SelectContent>
                </Select>
              </Field>
              <Field id="unit" label={t("metals.unit")}>
                <Select value={unit} onValueChange={setUnit}>
                  <SelectTrigger id="unit"><SelectValue /></SelectTrigger>
                  <SelectContent>{["GRAM", "MITHQAL", "TOLA"].map((u) => <SelectItem key={u} value={u}>{t(`metals.units.${u}`)}</SelectItem>)}</SelectContent>
                </Select>
              </Field>
            </div>
          )}
          <Field id="price" label={instrument === "USDIQD" ? t("metals.ratePrice", { base }) : t("metals.localPrice", { base })} hint={instrument === "USDIQD" ? undefined : t("metals.perGram24k", { amount: Math.round(perGram24kMicros / 1e6).toLocaleString() })}>
            <Input id="price" inputMode="decimal" dir="ltr" value={price} onChange={(e) => setPrice(e.target.value)} required autoFocus />
          </Field>
          <Field id="note" label={t("income.note")}>
            <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={120} placeholder={t("metals.overrideNoteHint")} />
          </Field>
          {view.overrides.length > 0 && <p className="text-xs opacity-70">{t("metals.overrideCount", { count: view.overrides.length })}</p>}
          <SheetFooter className="px-0"><Button type="submit" disabled={!price}>{t("accounts.save")}</Button></SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
