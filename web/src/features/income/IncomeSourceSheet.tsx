import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { createIncomeSource, RATE_SCALE, updateIncomeSource } from "@/db/income"
import type { Frequency, IncomeSource } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const FREQUENCIES: Frequency[] = ["MONTHLY", "BIWEEKLY", "WEEKLY", "ONE_OFF"]
const CURRENCIES = ["IQD", "USD"]

interface Props {
  open: boolean
  source: IncomeSource | null
  onClose: () => void
}

export function IncomeSourceSheet({ open, source, onClose }: Props) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [name, setName] = useState("")
  const [amount, setAmount] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [frequency, setFrequency] = useState<Frequency>("MONTHLY")
  const [payDay, setPayDay] = useState(25)
  const [anchorDate, setAnchorDate] = useState(todayIso())
  const [activeFrom, setActiveFrom] = useState(todayIso())
  const [activeTo, setActiveTo] = useState("")
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(source?.name ?? "")
    setAmount(source ? fromMinorUnits(source.amount, source.currency) : "0")
    setCurrency(source?.currency ?? base)
    setRate(source ? String(source.fxRateMicros / RATE_SCALE) : "1")
    setFrequency(source?.frequency ?? "MONTHLY")
    setPayDay(source?.payDay ?? 25)
    setAnchorDate(source?.anchorDate ?? todayIso())
    setActiveFrom(source?.activeFrom ?? todayIso())
    setActiveTo(source?.activeTo ?? "")
    setIsPrivate(source?.visibility === "PRIVATE")
  }, [open, source, base])

  const needsRate = currency !== base
  const usesPayDay = frequency === "MONTHLY"

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      amount: toMinorUnits(amount, currency),
      currency,
      fxRateMicros: needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE,
      frequency,
      payDay: usesPayDay ? payDay : null,
      anchorDate: usesPayDay ? null : anchorDate,
      activeFrom: frequency === "ONE_OFF" ? anchorDate : activeFrom,
      activeTo: frequency === "ONE_OFF" ? anchorDate : activeTo || null,
      note: null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (source) await updateIncomeSource(source.id, input)
    else await createIncomeSource(input)
    onClose()
  }

  async function endSource() {
    if (!source) return
    await updateIncomeSource(source.id, { activeTo: todayIso() })
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{source ? t("income.editSource") : t("income.addSource")}</SheetTitle>
        </SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="name" label={t("income.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="amount" label={t("income.amount")}>
              <Input id="amount" inputMode="decimal" dir="ltr" value={amount} onChange={(e) => setAmount(e.target.value)} required />
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>{CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          {needsRate && (
            <Field id="rate" label={t("income.fxRate", { from: currency, to: base })} hint={t("income.fxRateHint")}>
              <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
            </Field>
          )}
          <Field id="frequency" label={t("income.frequency")}>
            <Select value={frequency} onValueChange={(value) => setFrequency(value as Frequency)}>
              <SelectTrigger id="frequency"><SelectValue /></SelectTrigger>
              <SelectContent>{FREQUENCIES.map((f) => <SelectItem key={f} value={f}>{t(`income.frequencies.${f}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          {usesPayDay ? (
            <Field id="payDay" label={t("income.payDay")}>
              <Input id="payDay" type="number" min={1} max={31} dir="ltr" value={payDay} onChange={(e) => setPayDay(Number(e.target.value))} required />
            </Field>
          ) : (
            <Field id="anchorDate" label={frequency === "ONE_OFF" ? t("income.paidOn") : t("income.anchorDate")} hint={frequency === "ONE_OFF" ? undefined : t("income.anchorHint")}>
              <Input id="anchorDate" type="date" dir="ltr" value={anchorDate} onChange={(e) => setAnchorDate(e.target.value)} required />
            </Field>
          )}
          {frequency !== "ONE_OFF" && (
            <div className="grid grid-cols-2 gap-2">
              <Field id="activeFrom" label={t("income.activeFrom")}>
                <Input id="activeFrom" type="date" dir="ltr" value={activeFrom} onChange={(e) => setActiveFrom(e.target.value)} required />
              </Field>
              <Field id="activeTo" label={t("income.activeTo")}>
                <Input id="activeTo" type="date" dir="ltr" value={activeTo} onChange={(e) => setActiveTo(e.target.value)} />
              </Field>
            </div>
          )}
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {source && <Button type="button" variant="neutral" onClick={() => void endSource()}>{t("income.endSource")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
