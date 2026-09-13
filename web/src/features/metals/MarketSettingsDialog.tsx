import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { DisposalMethod, MarketSetting, RateKind } from "@/db/schema"
import { DEFAULT_SETTING, saveSetting } from "@/market/store"

const METHODS: DisposalMethod[] = ["FIFO", "SPECIFIC", "WEIGHTED_AVERAGE"]

export function MarketSettingsDialog({ open, setting, onClose }: { open: boolean; setting: MarketSetting | undefined; onClose: () => void }) {
  const { t } = useTranslation()
  const [rateKind, setRateKind] = useState<RateKind>(DEFAULT_SETTING.rateKind)
  const [goldPremium, setGoldPremium] = useState("0")
  const [silverPremium, setSilverPremium] = useState("0")
  const [goldMethod, setGoldMethod] = useState<DisposalMethod>("FIFO")
  const [silverMethod, setSilverMethod] = useState<DisposalMethod>("FIFO")

  useEffect(() => {
    if (!open) return
    setRateKind(setting?.rateKind ?? DEFAULT_SETTING.rateKind)
    setGoldPremium(String((setting?.goldPremiumBasisPoints ?? 0) / 100))
    setSilverPremium(String((setting?.silverPremiumBasisPoints ?? 0) / 100))
    setGoldMethod(setting?.goldMethod ?? "FIFO")
    setSilverMethod(setting?.silverMethod ?? "FIFO")
  }, [open, setting])

  async function submit(event: FormEvent) {
    event.preventDefault()
    await saveSetting({
      rateKind,
      goldPremiumBasisPoints: Math.round(Number(goldPremium) * 100),
      silverPremiumBasisPoints: Math.round(Number(silverPremium) * 100),
      goldMethod,
      silverMethod,
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
          <DialogFooter><Button type="submit">{t("accounts.save")}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
