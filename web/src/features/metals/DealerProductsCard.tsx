import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { api, unwrap } from "@/api/client"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { readMeta, writeMeta } from "@/db/meta"
import { formatMoney } from "@/domain/money/format"

interface Item { id: string; name_ar: string; name_en: string; weight_g: number; karat: number; purity_label: string; bid_iqd: number; ask_iqd: number }
interface Group { key: string; metal: string; title_ar: string; title_en: string; items: Item[] }
interface Catalogue { source: string; asOf: string; groups: Group[] }

const KEY = "market.catalogue"

/** The dealer's bars and coins with what they pay (bid) and charge (ask), as the feed lists them. */
export function DealerProductsCard({ currency }: { currency: string }) {
  const { t, i18n } = useTranslation()
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null)
  const [open, setOpen] = useState(false)
  const ar = i18n.language === "ar"
  const money = (v: number) => formatMoney(Math.round(v), currency, i18n.language)

  useEffect(() => {
    void (async () => {
      const cached = await readMeta<Catalogue>(KEY)
      if (cached) setCatalogue(cached)
      if (!navigator.onLine) return
      try {
        const response = await unwrap(api.GET("/api/v1/market/catalogue"))
        if (response && response.catalogue) {
          const next = { source: response.source ?? "", asOf: response.asOf ?? "", groups: response.catalogue as unknown as Group[] }
          await writeMeta(KEY, next)
          setCatalogue(next)
        }
      } catch {
        // offline or no dealer feed; the cached list stands
      }
    })()
  }, [])

  if (!catalogue) return null
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-2">
        <div>
          <CardTitle>{t("metals.dealerProducts")}</CardTitle>
          <CardDescription>{t("metals.dealerProductsBody", { source: catalogue.source, at: new Intl.DateTimeFormat(ar ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(catalogue.asOf)) })}</CardDescription>
        </div>
        <Button size="sm" variant="neutral" onClick={() => setOpen((o) => !o)}>{open ? t("metals.hide") : t("metals.show")}</Button>
      </CardHeader>
      {open && (
        <CardContent className="flex flex-col gap-4">
          {catalogue.groups.map((group) => (
            <div key={group.key}>
              <p className="mb-1 font-heading">{ar ? group.title_ar : group.title_en}</p>
              <table className="w-full text-sm">
                <thead><tr className="opacity-70"><th className="py-1 text-start">{t("metals.product")}</th><th className="py-1 text-end">{t("metals.dealerPays")}</th><th className="py-1 text-end">{t("metals.dealerCharges")}</th></tr></thead>
                <tbody>
                  {group.items.map((item) => (
                    <tr key={item.id} className="border-t border-border/40">
                      <td className="py-1">{ar ? item.name_ar : item.name_en}</td>
                      <td className="py-1 text-end tabular-nums">{money(item.bid_iqd)}</td>
                      <td className="py-1 text-end tabular-nums">{money(item.ask_iqd)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </CardContent>
      )}
    </Card>
  )
}
