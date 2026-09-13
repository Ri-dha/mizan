import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { premiumFor } from "@/db/metals"
import { MICROGRAMS_PER_TROY_OUNCE, PURITIES, metalValuation } from "@/domain/metal/valuation"
import { formatMoney } from "@/domain/money/format"
import type { MetalsView } from "./useMetals"

/** FR-MKT-02: every intermediate of BR-08, with the numbers currently in use. */
export function PriceExplainer({ open, view, onClose }: { open: boolean; view: MetalsView; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const gold = view.prices.spot.GOLD
  const premium = premiumFor(view.setting, "GOLD")
  const purity = PURITIES["21k"]
  const result = metalValuation({
    spotUsdPerOzMicros: gold.spotUsdPerOzMicros, usdIqdMicros: view.prices.usdIqdMicros, purityBasisPoints: purity.basisPoints,
    premiumBasisPoints: premium, premiumFixedPerGramMicros: 0, weightMg: 5000,
  })
  const money = (micros: number) => formatMoney(Math.round(micros / 1e6), base, i18n.language)

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{t("metals.howCalculated")}</DialogTitle></DialogHeader>
        <ol className="flex list-decimal flex-col gap-2 ps-5 text-sm">
          <li>{t("metals.explain.spot", { spot: (gold.spotUsdPerOzMicros / 1e6).toFixed(2), source: gold.source.label })}</li>
          <li>{t("metals.explain.rate", { rate: money(view.prices.usdIqdMicros), kind: t(`metals.rateKinds.${view.prices.rateKind}`), source: view.prices.rateSource.label })}</li>
          <li>{t("metals.explain.perGram", { ounce: (Number(MICROGRAMS_PER_TROY_OUNCE) / 1e6).toFixed(4), amount: money(result.perGram24kMicros) })}</li>
          <li>{t("metals.explain.purity", { purity: purity.label, factor: (purity.basisPoints / 10000).toFixed(3) })}</li>
          <li>{t("metals.explain.premium", { percent: premium / 100, amount: money(result.perGramMicros) })}</li>
          <li className="font-heading">{t("metals.explain.mithqal", { amount: formatMoney(result.value, base, i18n.language) })}</li>
        </ol>
        {gold.perGram24kOverrideMicros !== null && <p className="text-sm opacity-70">{t("metals.explain.overrideActive")}</p>}
      </DialogContent>
    </Dialog>
  )
}
