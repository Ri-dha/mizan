import { useEffect, useState, type FormEvent } from "react"
import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"

import { usePrivacyDefaults } from "@/db/privacy"

import { useSession } from "@/api/auth"
import { currentMonthKey } from "@/app/month"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { createDebt, updateDebt } from "@/db/debts"
import { RATE_SCALE } from "@/db/income"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import type { Debt, DebtDirection } from "@/db/schema"
import { softDelete } from "@/db/write"
import { todayIso } from "@/domain/calendar/month"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const CURRENCIES = ["IQD", "USD"]
const NONE = "__none__"

export function DebtSheet({ open, debt, onClose }: { open: boolean; debt: Debt | null; onClose: () => void }) {
  const { t } = useTranslation()
  const privacy = usePrivacyDefaults()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const buckets = useLiveQuery(async () => liveBucketsOf(planFor(await livePlans(), currentMonthKey(startDay))?.id), [startDay], [])
  const [name, setName] = useState("")
  const [counterparty, setCounterparty] = useState("")
  const [direction, setDirection] = useState<DebtDirection>("OWING")
  const [principal, setPrincipal] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [annualPercent, setAnnualPercent] = useState("0")
  const [termMonths, setTermMonths] = useState("")
  const [monthlyPayment, setMonthlyPayment] = useState("0")
  const [bucketId, setBucketId] = useState(NONE)
  const [startDate, setStartDate] = useState(todayIso())
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(debt?.name ?? "")
    setCounterparty(debt?.counterparty ?? "")
    setDirection(debt?.direction ?? "OWING")
    setPrincipal(debt ? fromMinorUnits(debt.principal, debt.currency) : "0")
    setCurrency(debt?.currency ?? base)
    setRate(debt ? String(debt.fxRateMicros / RATE_SCALE) : "1")
    setAnnualPercent(debt ? String(debt.annualRateBasisPoints / 100) : "0")
    setTermMonths(debt?.termMonths ? String(debt.termMonths) : "")
    setMonthlyPayment(debt ? fromMinorUnits(debt.monthlyPayment, debt.currency) : "0")
    setBucketId(debt?.bucketId ?? NONE)
    setStartDate(debt?.startDate ?? todayIso())
    setIsPrivate(debt ? debt.visibility === "PRIVATE" : privacy.debts === "PRIVATE")
  }, [open, debt, base, privacy])

  const needsRate = currency !== base

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      counterparty: counterparty.trim() || null,
      direction,
      principal: toMinorUnits(principal, currency),
      currency,
      fxRateMicros: needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE,
      annualRateBasisPoints: Math.round(Number(annualPercent) * 100),
      termMonths: termMonths ? Number(termMonths) : null,
      monthlyPayment: toMinorUnits(monthlyPayment, currency),
      bucketId: bucketId === NONE ? null : bucketId,
      startDate,
      note: null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (debt) await updateDebt(debt.id, input)
    else await createDebt(input)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{debt ? t("debts.edit") : t("debts.add")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <div className="grid grid-cols-2 gap-2">
            {(["OWING", "OWED"] as const).map((d) => (
              <Button key={d} type="button" size="sm" variant={direction === d ? "default" : "neutral"} onClick={() => setDirection(d)}>{t(`debts.directions.${d}`)}</Button>
            ))}
          </div>
          <Field id="name" label={t("income.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          <Field id="counterparty" label={t("debts.counterparty")}>
            <Input id="counterparty" value={counterparty} onChange={(e) => setCounterparty(e.target.value)} maxLength={80} />
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="principal" label={t("debts.principal")}>
              <Input id="principal" inputMode="decimal" dir="ltr" value={principal} onChange={(e) => setPrincipal(e.target.value)} required />
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
            <Field id="annualPercent" label={t("debts.annualRate")} hint={t("debts.rateHint")}>
              <Input id="annualPercent" inputMode="decimal" dir="ltr" value={annualPercent} onChange={(e) => setAnnualPercent(e.target.value)} required />
            </Field>
            <Field id="termMonths" label={t("debts.termMonths")}>
              <Input id="termMonths" type="number" min={1} dir="ltr" value={termMonths} onChange={(e) => setTermMonths(e.target.value)} />
            </Field>
          </div>
          <Field id="monthlyPayment" label={t("debts.monthlyPayment")}>
            <Input id="monthlyPayment" inputMode="decimal" dir="ltr" value={monthlyPayment} onChange={(e) => setMonthlyPayment(e.target.value)} required />
          </Field>
          {direction === "OWING" && (
            <Field id="bucket" label={t("debts.paidFromBucket")}>
              <Select value={bucketId} onValueChange={setBucketId}>
                <SelectTrigger id="bucket"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>{t("bills.noBucket")}</SelectItem>
                  {buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}
          <Field id="startDate" label={t("debts.startDate")}>
            <Input id="startDate" type="date" dir="ltr" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </Field>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {debt && <Button type="button" variant="neutral" onClick={() => void softDelete("debt", debt.id).then(onClose)}>{t("accounts.delete")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
