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
import { ATTRIBUTE_KEYS, DEFAULT_LIQUIDITY, createAsset, updateAsset } from "@/db/assets"
import type { Asset, AssetType } from "@/db/schema"
import type { DepreciationMethod } from "@/domain/asset/depreciation"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"
import { useMetals } from "@/features/metals/useMetals"

const TYPES: AssetType[] = ["VEHICLE", "PROPERTY", "ELECTRONICS", "EQUIPMENT", "FURNITURE", "LIVESTOCK", "OTHER"]
const METHODS: DepreciationMethod[] = ["NONE", "STRAIGHT_LINE", "DECLINING_BALANCE"]
const CURRENCIES = ["IQD", "USD"]
const NUMERIC_ATTRIBUTES: ReadonlySet<string> = new Set(["year", "mileageKm", "areaSqm", "headCount"])

interface Props {
  open: boolean
  asset: Asset | null
  onClose: () => void
  onDelete: (asset: Asset) => void
}

export function AssetSheet({ open, asset, onClose, onDelete }: Props) {
  const { t } = useTranslation()
  const privacy = usePrivacyDefaults()
  const session = useSession()
  const metals = useMetals()
  const base = session?.baseCurrency ?? "IQD"
  const [type, setType] = useState<AssetType>("VEHICLE")
  const [name, setName] = useState("")
  const [purchaseDate, setPurchaseDate] = useState("")
  const [price, setPrice] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [illiquid, setIlliquid] = useState(false)
  const [method, setMethod] = useState<DepreciationMethod>("NONE")
  const [rate, setRate] = useState("15")
  const [salvage, setSalvage] = useState("0")
  const [attributes, setAttributes] = useState<Record<string, string>>({})
  const [note, setNote] = useState("")
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setType(asset?.type ?? "VEHICLE")
    setName(asset?.name ?? "")
    setPurchaseDate(asset?.purchaseDate ?? "")
    setPrice(asset ? fromMinorUnits(asset.purchasePrice, asset.currency) : "0")
    setCurrency(asset?.currency ?? base)
    setIlliquid((asset?.liquidity ?? DEFAULT_LIQUIDITY.VEHICLE) === "ILLIQUID")
    setMethod(asset?.depreciationMethod ?? "NONE")
    setRate(asset ? String(asset.annualRateBasisPoints / 100) : "15")
    setSalvage(asset ? fromMinorUnits(asset.salvageValue, asset.currency) : "0")
    setAttributes(Object.fromEntries(Object.entries(asset?.attributes ?? {}).map(([k, v]) => [k, String(v)])))
    setNote(asset?.note ?? "")
    setIsPrivate(asset ? asset.visibility === "PRIVATE" : privacy.assets === "PRIVATE")
  }, [open, asset, base, privacy])

  function changeType(next: AssetType) {
    setType(next)
    if (!asset) setIlliquid(DEFAULT_LIQUIDITY[next] === "ILLIQUID")
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const kept = ATTRIBUTE_KEYS[type]
    const input = {
      type,
      name: name.trim(),
      purchaseDate: purchaseDate || null,
      purchasePrice: toMinorUnits(price, currency),
      currency,
      fxRateMicros: asset?.currency === currency ? asset.fxRateMicros : currency === base ? 1_000_000 : currency === "USD" ? Math.round(metals.prices.usdIqdMicros / 100) : 1_000_000,
      liquidity: illiquid ? ("ILLIQUID" as const) : ("LIQUID" as const),
      depreciationMethod: method,
      annualRateBasisPoints: method === "NONE" ? 0 : Math.round(Number(rate) * 100),
      salvageValue: method === "NONE" ? 0 : toMinorUnits(salvage, currency),
      attributes: Object.fromEntries(kept.filter((k) => attributes[k]?.trim()).map((k) => [k, NUMERIC_ATTRIBUTES.has(k) ? Number(attributes[k]) : attributes[k].trim()])),
      note: note.trim() || null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (asset) await updateAsset(asset.id, input)
    else await createAsset(input)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{asset ? t("assets.edit") : t("assets.add")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="type" label={t("assets.type")}>
            <Select value={type} onValueChange={(v) => changeType(v as AssetType)}>
              <SelectTrigger id="type"><SelectValue /></SelectTrigger>
              <SelectContent>{TYPES.map((k) => <SelectItem key={k} value={k}>{t(`assets.types.${k}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <Field id="name" label={t("accounts.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          {ATTRIBUTE_KEYS[type].map((key) => (
            <Field key={key} id={`attr-${key}`} label={t(`assets.attributes.${key}`)}>
              <Input id={`attr-${key}`} inputMode={NUMERIC_ATTRIBUTES.has(key) ? "numeric" : undefined} dir={NUMERIC_ATTRIBUTES.has(key) ? "ltr" : undefined}
                value={attributes[key] ?? ""} onChange={(e) => setAttributes({ ...attributes, [key]: e.target.value })} maxLength={120} />
            </Field>
          ))}
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="price" label={t("assets.purchasePrice")}>
              <Input id="price" inputMode="decimal" dir="ltr" value={price} onChange={(e) => setPrice(e.target.value)} required />
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>{CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <Field id="purchaseDate" label={t("assets.purchaseDate")}>
            <Input id="purchaseDate" type="date" dir="ltr" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} />
          </Field>
          <Field id="method" label={t("assets.depreciation")}>
            <Select value={method} onValueChange={(v) => setMethod(v as DepreciationMethod)}>
              <SelectTrigger id="method"><SelectValue /></SelectTrigger>
              <SelectContent>{METHODS.map((m) => <SelectItem key={m} value={m}>{t(`assets.methods.${m}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          {method !== "NONE" && (
            <div className="grid grid-cols-2 gap-2">
              <Field id="rate" label={t("assets.annualRate")}>
                <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
              </Field>
              <Field id="salvage" label={t("assets.salvage")}>
                <Input id="salvage" inputMode="decimal" dir="ltr" value={salvage} onChange={(e) => setSalvage(e.target.value)} />
              </Field>
            </div>
          )}
          <p className="text-xs opacity-70">{t(`assets.methodHints.${method}`)}</p>
          <div className="flex items-center gap-2">
            <Switch id="illiquid" checked={illiquid} onCheckedChange={setIlliquid} />
            <Label htmlFor="illiquid">{t("assets.illiquid")}</Label>
          </div>
          <Field id="note" label={t("transactions.note")}>
            <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} />
          </Field>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {asset && <Button type="button" variant="neutral" onClick={() => onDelete(asset)}>{t("accounts.delete")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
