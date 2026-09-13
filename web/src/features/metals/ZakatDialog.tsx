import { useState } from "react"
import { useTranslation } from "react-i18next"

import { useDateFormat } from "@/app/dates"
import { Field } from "@/components/Field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import type { LotValuation } from "@/db/metals"
import { todayIso } from "@/domain/calendar/month"
import { formatHijri } from "@/domain/calendar/hijri"
import { formatMoney } from "@/domain/money/format"
import { valueOf } from "@/domain/metal/valuation"

/** Classical nisab: 20 mithqal of gold (85 g) or 200 dirhams of silver (595 g); both editable. */
const DEFAULT_NISAB_GOLD_G = 85
const DEFAULT_NISAB_SILVER_G = 595
const ZAKAT_RATE_BP = 250
const BP = 10_000
const MG = 1000

interface Props {
  open: boolean
  valued: LotValuation[]
  perGram24k: Record<"GOLD" | "SILVER", number>
  currency: string
  onClose: () => void
}

/** FR-MTL-12: the base and the inputs only; the dialog says so in its first line and makes no ruling. */
export function ZakatDialog({ open, valued, perGram24k, currency, onClose }: Props) {
  const { t, i18n } = useTranslation()
  const date = useDateFormat()
  const [asOf, setAsOf] = useState(todayIso())
  const [nisabGold, setNisabGold] = useState(String(DEFAULT_NISAB_GOLD_G))
  const [nisabSilver, setNisabSilver] = useState(String(DEFAULT_NISAB_SILVER_G))
  const [goldPrice, setGoldPrice] = useState("")
  const [silverPrice, setSilverPrice] = useState("")
  const money = (v: number) => formatMoney(v, currency, i18n.language)

  const metalRow = (metal: "GOLD" | "SILVER", nisab: string, priceInput: string) => {
    const held = valued.filter((v) => v.lot.metal === metal && v.remainingMg > 0 && v.lot.purchaseDate <= asOf)
    const pureMg = held.reduce((sum, v) => sum + Math.round((v.remainingMg * v.lot.purityBasisPoints) / BP), 0)
    const perGramMicros = priceInput.trim() ? Math.round(Number(priceInput) * 1_000_000) : perGram24k[metal]
    const value = valueOf(perGramMicros, pureMg)
    const nisabMg = Math.round(Number(nisab) * MG)
    return { pureMg, value, reaches: nisabMg > 0 && pureMg >= nisabMg, base: Math.round((value * ZAKAT_RATE_BP) / BP) }
  }
  const gold = metalRow("GOLD", nisabGold, goldPrice)
  const silver = metalRow("SILVER", nisabSilver, silverPrice)
  const grams = (mg: number) => `${new Intl.NumberFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { maximumFractionDigits: 3 }).format(mg / MG)} g`

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("zakat.title")}</DialogTitle>
          <DialogDescription>{t("zakat.disclaimer")}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <Field id="asOf" label={t("zakat.asOf")} hint={formatHijri(asOf, i18n.language)}>
            <Input id="asOf" type="date" dir="ltr" value={asOf} onChange={(e) => setAsOf(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field id="nisabGold" label={t("zakat.nisab", { metal: t("metals.metal.GOLD") })}>
              <Input id="nisabGold" inputMode="decimal" dir="ltr" value={nisabGold} onChange={(e) => setNisabGold(e.target.value)} />
            </Field>
            <Field id="nisabSilver" label={t("zakat.nisab", { metal: t("metals.metal.SILVER") })}>
              <Input id="nisabSilver" inputMode="decimal" dir="ltr" value={nisabSilver} onChange={(e) => setNisabSilver(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="goldPrice" label={t("zakat.price", { metal: t("metals.metal.GOLD") })} hint={t("zakat.priceHint", { price: money(Math.round(perGram24k.GOLD / 1e6)) })}>
              <Input id="goldPrice" inputMode="decimal" dir="ltr" value={goldPrice} onChange={(e) => setGoldPrice(e.target.value)} />
            </Field>
            <Field id="silverPrice" label={t("zakat.price", { metal: t("metals.metal.SILVER") })} hint={t("zakat.priceHint", { price: money(Math.round(perGram24k.SILVER / 1e6)) })}>
              <Input id="silverPrice" inputMode="decimal" dir="ltr" value={silverPrice} onChange={(e) => setSilverPrice(e.target.value)} />
            </Field>
          </div>
          {([["GOLD", gold], ["SILVER", silver]] as const).map(([metal, row]) => (
            <div key={metal} className="rounded-base border-2 border-border p-3 text-sm">
              <p className="font-heading">{t(`metals.metal.${metal}`)}</p>
              <p>{t("zakat.pureWeight", { weight: grams(row.pureMg) })}</p>
              <p>{t("zakat.value", { value: money(row.value), date: date(asOf) })}</p>
              <p>{row.reaches ? t("zakat.reaches") : t("zakat.below")}</p>
              {row.reaches && <p className="font-heading">{t("zakat.base", { amount: money(row.base) })}</p>}
            </div>
          ))}
          <Alert><AlertDescription>{t("zakat.footer")}</AlertDescription></Alert>
        </div>
      </DialogContent>
    </Dialog>
  )
}
