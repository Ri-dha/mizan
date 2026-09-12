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
import { createRecurringExpense, updateRecurringExpense } from "@/db/expenses"
import { RATE_SCALE } from "@/db/income"
import type { Bucket, ExpenseFrequency, RecurringExpense } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const FREQUENCIES: ExpenseFrequency[] = ["MONTHLY", "QUARTERLY", "ANNUAL", "CUSTOM"]
const CURRENCIES = ["IQD", "USD"]
const NONE = "__none__"

interface Props {
  open: boolean
  expense: RecurringExpense | null
  buckets: Bucket[]
  onClose: () => void
}

export function RecurringExpenseSheet({ open, expense, buckets, onClose }: Props) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [name, setName] = useState("")
  const [bucketId, setBucketId] = useState<string>(NONE)
  const [amount, setAmount] = useState("0")
  const [isEstimate, setIsEstimate] = useState(false)
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [frequency, setFrequency] = useState<ExpenseFrequency>("MONTHLY")
  const [dueDay, setDueDay] = useState(1)
  const [anchorDate, setAnchorDate] = useState(todayIso())
  const [intervalDays, setIntervalDays] = useState(30)
  const [activeFrom, setActiveFrom] = useState(todayIso())
  const [activeTo, setActiveTo] = useState("")
  const [category, setCategory] = useState("")
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(expense?.name ?? "")
    setBucketId(expense?.bucketId ?? buckets[0]?.id ?? NONE)
    setAmount(expense ? fromMinorUnits(expense.amount, expense.currency) : "0")
    setIsEstimate(expense?.isEstimate ?? false)
    setCurrency(expense?.currency ?? base)
    setRate(expense ? String(expense.fxRateMicros / RATE_SCALE) : "1")
    setFrequency(expense?.frequency ?? "MONTHLY")
    setDueDay(expense?.dueDay ?? 1)
    setAnchorDate(expense?.anchorDate ?? todayIso())
    setIntervalDays(expense?.intervalDays ?? 30)
    setActiveFrom(expense?.activeFrom ?? todayIso())
    setActiveTo(expense?.activeTo ?? "")
    setCategory(expense?.category ?? "")
    setIsPrivate(expense?.visibility === "PRIVATE")
  }, [open, expense, buckets, base])

  const needsRate = currency !== base

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      bucketId: bucketId === NONE ? null : bucketId,
      amount: toMinorUnits(amount, currency),
      isEstimate,
      currency,
      fxRateMicros: needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE,
      frequency,
      dueDay: frequency === "MONTHLY" ? dueDay : null,
      anchorDate: frequency === "MONTHLY" ? null : anchorDate,
      intervalDays: frequency === "CUSTOM" ? intervalDays : null,
      activeFrom,
      activeTo: activeTo || null,
      category: category.trim() || null,
      note: null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (expense) await updateRecurringExpense(expense.id, input)
    else await createRecurringExpense(input)
    onClose()
  }

  async function endExpense() {
    if (!expense) return
    await updateRecurringExpense(expense.id, { activeTo: todayIso() })
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{expense ? t("bills.edit") : t("bills.add")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="name" label={t("income.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          <Field id="bucket" label={t("transactions.bucket")}>
            <Select value={bucketId} onValueChange={setBucketId}>
              <SelectTrigger id="bucket"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{t("bills.noBucket")}</SelectItem>
                {buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}
              </SelectContent>
            </Select>
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
            <Field id="rate" label={t("income.fxRate", { from: currency, to: base })}>
              <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
            </Field>
          )}
          <div className="flex items-center gap-2">
            <Switch id="estimate" checked={isEstimate} onCheckedChange={setIsEstimate} />
            <Label htmlFor="estimate">{t("bills.isEstimate")}</Label>
          </div>
          <Field id="frequency" label={t("income.frequency")}>
            <Select value={frequency} onValueChange={(v) => setFrequency(v as ExpenseFrequency)}>
              <SelectTrigger id="frequency"><SelectValue /></SelectTrigger>
              <SelectContent>{FREQUENCIES.map((f) => <SelectItem key={f} value={f}>{t(`bills.frequencies.${f}`)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          {frequency === "MONTHLY" ? (
            <Field id="dueDay" label={t("bills.dueDay")}>
              <Input id="dueDay" type="number" min={1} max={31} dir="ltr" value={dueDay} onChange={(e) => setDueDay(Number(e.target.value))} required />
            </Field>
          ) : (
            <Field id="anchorDate" label={t("bills.anchorDate")} hint={t("bills.anchorHint")}>
              <Input id="anchorDate" type="date" dir="ltr" value={anchorDate} onChange={(e) => setAnchorDate(e.target.value)} required />
            </Field>
          )}
          {frequency === "CUSTOM" && (
            <Field id="intervalDays" label={t("bills.everyDays")}>
              <Input id="intervalDays" type="number" min={1} dir="ltr" value={intervalDays} onChange={(e) => setIntervalDays(Number(e.target.value))} required />
            </Field>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Field id="activeFrom" label={t("income.activeFrom")}>
              <Input id="activeFrom" type="date" dir="ltr" value={activeFrom} onChange={(e) => setActiveFrom(e.target.value)} required />
            </Field>
            <Field id="activeTo" label={t("income.activeTo")}>
              <Input id="activeTo" type="date" dir="ltr" value={activeTo} onChange={(e) => setActiveTo(e.target.value)} />
            </Field>
          </div>
          <Field id="category" label={t("transactions.category")}>
            <Input id="category" value={category} onChange={(e) => setCategory(e.target.value)} maxLength={40} />
          </Field>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {expense && <Button type="button" variant="neutral" onClick={() => void endExpense()}>{t("bills.end")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
