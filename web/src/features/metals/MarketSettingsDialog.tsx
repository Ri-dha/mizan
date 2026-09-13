import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { DisposalMethod, MarketSetting, RateKind, ValuationBasis } from "@/db/schema"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { DEFAULT_SETTING, saveSetting } from "@/market/store"

const METHODS: DisposalMethod[] = ["FIFO", "SPECIFIC", "WEIGHTED_AVERAGE"]

export function MarketSettingsDialog({ open, setting, onClose }: { open: boolean; setting: MarketSetting | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const [rateKind, setRateKind] = useState<RateKind>(DEFAULT_SETTING.rateKind)
  const [goldPremium, setGoldPremium] = useState("0")
  const [silverPremium, setSilverPremium] = useState("0")
  const [goldMethod, setGoldMethod] = useState<DisposalMethod>("FIFO")
  const [silverMethod, setSilverMethod] = useState<DisposalMethod>("FIFO")
  const [basis, setBasis] = useState<ValuationBasis>("MARKET")
  const [goldBuyback, setGoldBuyback] = useState("0")
  const [silverBuyback, setSilverBuyback] = useState("0")

  useEffect(() => {
    if (!open) return
    setRateKind(setting?.rateKind ?? DEFAULT_SETTING.rateKind)
    setGoldPremium(String((setting?.goldPremiumBasisPoints ?? 0) / 100))
    setSilverPremium(String((setting?.silverPremiumBasisPoints ?? 0) / 100))
    setGoldMethod(setting?.goldMethod ?? "FIFO")
    setSilverMethod(setting?.silverMethod ?? "FIFO")
    setBasis(setting?.valuationBasis ?? "MARKET")
    setGoldBuyback(String((setting?.goldBuybackBasisPoints ?? 0) / 100))
    setSilverBuyback(String((setting?.silverBuybackBasisPoints ?? 0) / 100))
  }, [open, setting])

  async function submit(event: FormEvent) {
    event.preventDefault()
    await saveSetting({
      rateKind,
      goldPremiumBasisPoints: Math.round(Number(goldPremium) * 100),
      silverPremiumBasisPoints: Math.round(Number(silverPremium) * 100),
      goldMethod,
      silverMethod,
      valuationBasis: basis,
      goldBuybackBasisPoints: Math.round(Number(goldBuyback) * 100),
      silverBuybackBasisPoints: Math.round(Number(silverBuyback) * 100),
    })
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{t("metals.settings")}</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Field id="rateKind" label={t("metals.rateKind")} hint={t("metals.rateKindHint")}>
            <Select value={rateKind} onValueChange={(v) => setRateKind(v as RateKind)}>
              <SelectTrigger id="rateKind"><SelectValue /></SelectTrigger>
              <SelectContent>{(["PARALLEL", "OFFICIAL"] as const).map((k) => <SelectItem key={k} value={k}>{t(`metals.rateKinds.${k}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field id="goldPremium" label={t("metals.premium", { metal: t("metals.metal.GOLD") })} hint={t("metals.premiumHint")}>
              <Input id="goldPremium" inputMode="decimal" dir="ltr" value={goldPremium} onChange={(e) => setGoldPremium(e.target.value)} />
            </Field>
            <Field id="silverPremium" label={t("metals.premium", { metal: t("metals.metal.SILVER") })}>
              <Input id="silverPremium" inputMode="decimal" dir="ltr" value={silverPremium} onChange={(e) => setSilverPremium(e.target.value)} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="goldMethod" label={t("metals.defaultMethod", { metal: t("metals.metal.GOLD") })}>
              <Select value={goldMethod} onValueChange={(v) => setGoldMethod(v as DisposalMethod)}>
                <SelectTrigger id="goldMethod"><SelectValue /></SelectTrigger>
                <SelectContent>{METHODS.map((m) => <SelectItem key={m} value={m}>{t(`metals.methods.${m}`)}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
            <Field id="silverMethod" label={t("metals.defaultMethod", { metal: t("metals.metal.SILVER") })}>
              <Select value={silverMethod} onValueChange={(v) => setSilverMethod(v as DisposalMethod)}>
                <SelectTrigger id="silverMethod"><SelectValue /></SelectTrigger>
                <SelectContent>{METHODS.map((m) => <SelectItem key={m} value={m}>{t(`metals.methods.${m}`)}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <p className="text-xs opacity-70">{t("metals.methodLocked")}</p>
          <div className="flex items-center gap-2">
            <Switch id="buyback" checked={basis === "BUYBACK"} onCheckedChange={(on) => setBasis(on ? "BUYBACK" : "MARKET")} />
            <Label htmlFor="buyback">{t("metals.buybackBasis")}</Label>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="goldBuyback" label={t("metals.buybackSpread", { metal: t("metals.metal.GOLD") })} hint={t("metals.buybackHint")}>
              <Input id="goldBuyback" inputMode="decimal" dir="ltr" value={goldBuyback} onChange={(e) => setGoldBuyback(e.target.value)} />
            </Field>
            <Field id="silverBuyback" label={t("metals.buybackSpread", { metal: t("metals.metal.SILVER") })}>
              <Input id="silverBuyback" inputMode="decimal" dir="ltr" value={silverBuyback} onChange={(e) => setSilverBuyback(e.target.value)} />
            </Field>
          </div>
          <DialogFooter><Button type="submit">{t("accounts.save")}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
