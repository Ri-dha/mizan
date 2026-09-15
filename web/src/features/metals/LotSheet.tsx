import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { usePrivacyDefaults } from "@/db/privacy"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { RATE_SCALE } from "@/db/income"
import { createLot, removeLot, updateLot } from "@/db/metals"
import type { Metal, MetalForm, MetalLot, WeightUnit } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { PURITIES, toMilligrams, valueOf } from "@/domain/metal/valuation"
import { perGramFor } from "@/db/metals"
import { useMetals } from "./useMetals"
import { formatMoney } from "@/domain/money/format"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const UNITS: WeightUnit[] = ["GRAM", "MITHQAL", "TOLA", "TROY_OUNCE", "KILOGRAM"]
const FORMS: MetalForm[] = ["JEWELLERY", "COIN", "BAR", "SCRAP"]
const CURRENCIES = ["IQD", "USD"]

export function LotSheet({ open, lot, onClose }: { open: boolean; lot: MetalLot | null; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const privacy = usePrivacyDefaults()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [metal, setMetal] = useState<Metal>("GOLD")
  const [purity, setPurity] = useState("21k")
  const [quantity, setQuantity] = useState("1")
  const [unit, setUnit] = useState<WeightUnit>("MITHQAL")
  const [purchaseDate, setPurchaseDate] = useState(todayIso())
  const [metalCost, setMetalCost] = useState("0")
  const [making, setMaking] = useState("0")
  const [fees, setFees] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [form, setForm] = useState<MetalForm>("JEWELLERY")
  const [dealer, setDealer] = useState("")
  const [location, setLocation] = useState("")
  const [serial, setSerial] = useState("")
  const [heldFor, setHeldFor] = useState("")
  const [isPrivate, setIsPrivate] = useState(false)
  const [costTouched, setCostTouched] = useState(false)
  const market = useMetals()

  useEffect(() => {
    if (!open) return
    setCostTouched(lot !== null)
    setMetal(lot?.metal ?? "GOLD")
    setPurity(lot?.purityLabel ?? "21k")
    setQuantity(lot?.quantityEntered ?? "1")
    setUnit(lot?.weightUnitEntered ?? "MITHQAL")
    setPurchaseDate(lot?.purchaseDate ?? todayIso())
    setMetalCost(lot ? fromMinorUnits(lot.metalCost, lot.currency) : "0")
    setMaking(lot ? fromMinorUnits(lot.makingCharge, lot.currency) : "0")
    setFees(lot ? fromMinorUnits(lot.fees, lot.currency) : "0")
    setCurrency(lot?.currency ?? base)
    setRate(lot ? String(lot.fxRateMicros / RATE_SCALE) : "1")
    setForm(lot?.form ?? "JEWELLERY")
    setDealer(lot?.dealer ?? "")
    setLocation(lot?.location ?? "")
    setSerial(lot?.serial ?? "")
    setHeldFor(lot?.heldFor ?? "")
    setIsPrivate(lot ? lot.visibility === "PRIVATE" : privacy.metals === "PRIVATE")
  }, [open, lot, base, privacy])

  const purities = Object.values(PURITIES).filter((p) => p.metal === metal)
  const weightMg = toMilligrams(Number(quantity) || 0, unit)
  const needsRate = currency !== base

  // Today's price for what is being entered, in the lot's currency; a cost the user has not typed follows it.
  const perGramMicros = perGramFor(metal, PURITIES[purity]?.basisPoints ?? 0, market.prices, market.setting)
  const todayBase = valueOf(perGramMicros, weightMg)
  const todayInCurrency = currency === base ? todayBase : market.prices.usdIqdMicros > 0 ? Math.round((todayBase * 1e6 * 100) / market.prices.usdIqdMicros) : 0
  const hasPrice = perGramMicros > 0 && weightMg > 0
  useEffect(() => {
    if (!open || lot || costTouched || !hasPrice) return
    setMetalCost(fromMinorUnits(todayInCurrency, currency))
  }, [open, lot, costTouched, hasPrice, todayInCurrency, currency])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      metal,
      purityLabel: purity,
      purityBasisPoints: PURITIES[purity].basisPoints,
      weightMg,
      weightUnitEntered: unit,
      quantityEntered: quantity,
      purchaseDate,
      metalCost: toMinorUnits(metalCost, currency),
      makingCharge: toMinorUnits(making, currency),
      fees: toMinorUnits(fees, currency),
      currency,
      fxRateMicros: needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE,
      form,
      dealer: dealer.trim() || null,
      location: location.trim() || null,
      serial: serial.trim() || null,
      heldFor: heldFor.trim() || null,
      note: null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (lot) await updateLot(lot.id, input)
    else await createLot(input)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{lot ? t("metals.editLot") : t("metals.addLot")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <div className="grid grid-cols-2 gap-2">
            {(["GOLD", "SILVER"] as const).map((m) => (
              <Button key={m} type="button" size="sm" variant={metal === m ? "default" : "neutral"} onClick={() => { setMetal(m); setPurity(m === "GOLD" ? "21k" : "999") }}>{t(`metals.metal.${m}`)}</Button>
            ))}
          </div>
          <Field id="purity" label={t("metals.purity")}>
            <Select value={purity} onValueChange={setPurity}>
              <SelectTrigger id="purity"><SelectValue /></SelectTrigger>
              <SelectContent>{purities.map((p) => <SelectItem key={p.label} value={p.label}>{p.label} ({p.basisPoints / 100}%)</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="quantity" label={t("metals.weight")} hint={t("metals.inGrams", { grams: (weightMg / 1000).toFixed(3) })}>
              <Input id="quantity" inputMode="decimal" dir="ltr" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
            </Field>
            <Field id="unit" label={t("metals.unit")}>
              <Select value={unit} onValueChange={(v) => setUnit(v as WeightUnit)}>
                <SelectTrigger id="unit" className="w-32"><SelectValue /></SelectTrigger>
                <SelectContent>{UNITS.map((u) => <SelectItem key={u} value={u}>{t(`metals.units.${u}`)}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <Field id="form" label={t("metals.form")}>
            <Select value={form} onValueChange={(v) => setForm(v as MetalForm)}>
              <SelectTrigger id="form"><SelectValue /></SelectTrigger>
              <SelectContent>{FORMS.map((f) => <SelectItem key={f} value={f}>{t(`metals.forms.${f}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <Field id="purchaseDate" label={t("metals.purchaseDate")}>
            <Input id="purchaseDate" type="date" dir="ltr" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} required />
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="metalCost" label={t("metals.metalCost")} hint={hasPrice ? t("metals.todayHint", { perGram: formatMoney(Math.round(perGramMicros / 1e6), base, i18n.language), total: formatMoney(todayInCurrency, currency, i18n.language) }) : undefined}>
              <Input id="metalCost" inputMode="decimal" dir="ltr" value={metalCost} onChange={(e) => { setCostTouched(true); setMetalCost(e.target.value) }} required />
              {hasPrice && costTouched && <Button type="button" size="sm" variant="neutral" className="mt-1" onClick={() => { setMetalCost(fromMinorUnits(todayInCurrency, currency)); setCostTouched(false) }}>{t("metals.useToday")}</Button>}
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>{CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          {needsRate && (
            <Field id="rate" label={t("income.fxRate", { from: currency, to: base })}>
              <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
            </Field>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Field id="making" label={t("metals.makingCharge")} hint={t("metals.makingHint")}>
              <Input id="making" inputMode="decimal" dir="ltr" value={making} onChange={(e) => setMaking(e.target.value)} />
            </Field>
            <Field id="fees" label={t("metals.fees")}>
              <Input id="fees" inputMode="decimal" dir="ltr" value={fees} onChange={(e) => setFees(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="dealer" label={t("metals.dealer")}>
              <Input id="dealer" value={dealer} onChange={(e) => setDealer(e.target.value)} maxLength={80} />
            </Field>
            <Field id="location" label={t("metals.location")}>
              <Input id="location" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={80} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="serial" label={t("metals.serial")}>
              <Input id="serial" value={serial} onChange={(e) => setSerial(e.target.value)} maxLength={80} />
            </Field>
            <Field id="heldFor" label={t("metals.heldFor")}>
              <Input id="heldFor" value={heldFor} onChange={(e) => setHeldFor(e.target.value)} maxLength={80} />
            </Field>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {lot && <Button type="button" variant="neutral" onClick={() => void removeLot(lot.id).then(onClose)}>{t("accounts.delete")}</Button>}
            <Button type="submit" disabled={weightMg <= 0}>{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
