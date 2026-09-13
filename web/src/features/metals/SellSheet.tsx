import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { RATE_SCALE, toBaseAmount } from "@/db/income"
import { recordSale } from "@/db/metals"
import type { DisposalMethod, Metal, WeightUnit } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { InsufficientWeightError, planDisposal } from "@/domain/metal/disposal"
import { toMilligrams } from "@/domain/metal/valuation"
import { formatMoney, toMinorUnits } from "@/domain/money/format"
import type { MetalsView } from "./useMetals"

const UNITS: WeightUnit[] = ["GRAM", "MITHQAL", "TOLA", "TROY_OUNCE", "KILOGRAM"]
const METHODS: DisposalMethod[] = ["FIFO", "SPECIFIC", "WEIGHTED_AVERAGE"]

export function SellSheet({ metal, view, onClose }: { metal: Metal | null; view: MetalsView; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [quantity, setQuantity] = useState("1")
  const [unit, setUnit] = useState<WeightUnit>("MITHQAL")
  const [proceeds, setProceeds] = useState("0")
  const [fees, setFees] = useState("0")
  const [soldOn, setSoldOn] = useState(todayIso())
  const [method, setMethod] = useState<DisposalMethod>("FIFO")
  const [chosen, setChosen] = useState<string[]>([])
  const [buyer, setBuyer] = useState("")

  useEffect(() => {
    if (!metal) return
    setQuantity("1"); setUnit("MITHQAL"); setProceeds("0"); setFees("0"); setSoldOn(todayIso()); setChosen([]); setBuyer("")
    setMethod(metal === "GOLD" ? (view.setting?.goldMethod ?? "FIFO") : (view.setting?.silverMethod ?? "FIFO"))
  }, [metal, view.setting])

  if (!metal) return null

  const candidates = view.valued.filter((v) => v.lot.metal === metal && v.remainingMg > 0)
  const weightMg = toMilligrams(Number(quantity) || 0, unit)
  const proceedsBase = toBaseAmount(toMinorUnits(proceeds, base) - toMinorUnits(fees, base), RATE_SCALE)
  let preview: ReturnType<typeof planDisposal> | null = null
  let previewError: string | null = null
  try {
    preview = planDisposal(
      candidates.map((v) => ({ id: v.lot.id, purchaseDate: v.lot.purchaseDate, weightMg: v.lot.weightMg, remainingMg: v.remainingMg, metalCost: v.metalCostBase, makingCharge: v.makingBase, fees: v.feesBase })),
      weightMg, method, chosen, proceedsBase,
    )
  } catch (e) {
    previewError = e instanceof InsufficientWeightError ? t("metals.notEnough") : String(e)
  }

  const money = (v: number) => formatMoney(v, base, i18n.language)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!metal) return
    try {
      await recordSale(view.valued, {
        metal, weightMg, proceeds: toMinorUnits(proceeds, base), fees: toMinorUnits(fees, base), currency: base, fxRateMicros: RATE_SCALE,
        soldOn, method, specificLotIds: chosen, buyer: buyer.trim() || null, note: null,
      })
      toast(t("metals.sold"))
      onClose()
    } catch (e) {
      toast(e instanceof InsufficientWeightError ? t("metals.notEnough") : String(e))
    }
  }

  return (
    <Sheet open onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{t("metals.sell", { metal: t(`metals.metal.${metal}`) })}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="quantity" label={t("metals.weight")}>
              <Input id="quantity" inputMode="decimal" dir="ltr" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
            </Field>
            <Field id="unit" label={t("metals.unit")}>
              <Select value={unit} onValueChange={(v) => setUnit(v as WeightUnit)}>
                <SelectTrigger id="unit" className="w-32"><SelectValue /></SelectTrigger>
                <SelectContent>{UNITS.map((u) => <SelectItem key={u} value={u}>{t(`metals.units.${u}`)}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="proceeds" label={t("metals.proceeds")}>
              <Input id="proceeds" inputMode="decimal" dir="ltr" value={proceeds} onChange={(e) => setProceeds(e.target.value)} required />
            </Field>
            <Field id="fees" label={t("metals.fees")}>
              <Input id="fees" inputMode="decimal" dir="ltr" value={fees} onChange={(e) => setFees(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="soldOn" label={t("transactions.date")}>
              <Input id="soldOn" type="date" dir="ltr" value={soldOn} onChange={(e) => setSoldOn(e.target.value)} required />
            </Field>
            <Field id="buyer" label={t("metals.buyer")}>
              <Input id="buyer" value={buyer} onChange={(e) => setBuyer(e.target.value)} maxLength={80} />
            </Field>
          </div>
          <Field id="method" label={t("metals.method")} hint={t("metals.methodHint")}>
            <Select value={method} onValueChange={(v) => setMethod(v as DisposalMethod)}>
              <SelectTrigger id="method"><SelectValue /></SelectTrigger>
              <SelectContent>{METHODS.map((m) => <SelectItem key={m} value={m}>{t(`metals.methods.${m}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          {method === "SPECIFIC" && (
            <div className="flex flex-col gap-2">
              <Label>{t("metals.chooseLots")}</Label>
              {candidates.map((v) => (
                <div key={v.lot.id} className="flex items-center gap-2 text-sm">
                  <Switch checked={chosen.includes(v.lot.id)} onCheckedChange={(on) => setChosen(on ? [...chosen, v.lot.id] : chosen.filter((id) => id !== v.lot.id))} />
                  <span>{v.lot.purchaseDate} · {v.lot.purityLabel} · {(v.remainingMg / 1000).toFixed(3)} g · {money(v.costPerGramBase)}/g</span>
                </div>
              ))}
            </div>
          )}
          <div className="rounded-base border-2 border-border p-3 text-sm">
            {previewError ? <p>{previewError}</p> : preview && (
              <>
                <p>{t("metals.costLeaving", { amount: money(preview.costBasis) })}</p>
                <p className="font-heading">{t("metals.realised", { amount: (preview.realisedGain > 0 ? "+" : "") + money(preview.realisedGain) })}</p>
                <p className="opacity-70">{t("metals.excludingMaking", { amount: (preview.realisedGainExcludingMaking > 0 ? "+" : "") + money(preview.realisedGainExcludingMaking) })}</p>
              </>
            )}
          </div>
          <SheetFooter className="px-0">
            <Button type="submit" disabled={!preview}>{t("metals.confirmSale")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
