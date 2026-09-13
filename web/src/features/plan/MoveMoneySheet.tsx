import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { moveMoney } from "@/db/plan"
import type { Bucket } from "@/db/schema"
import { monthKeyFor, monthWindow, todayIso } from "@/domain/calendar/month"
import { formatMoney, toMinorUnits } from "@/domain/money/format"
import type { MonthFiguresResult } from "@/domain/plan/figures"

interface Props {
  open: boolean
  buckets: Bucket[]
  figures: MonthFiguresResult
  monthKey: string
  startDay: number
  currency: string
  onClose: () => void
}

export function MoveMoneySheet({ open, buckets, figures, monthKey, startDay, currency, onClose }: Props) {
  const { t, i18n } = useTranslation()
  const [fromId, setFromId] = useState("")
  const [toId, setToId] = useState("")
  const [amount, setAmount] = useState("")
  const [reason, setReason] = useState("")
  const [on, setOn] = useState(todayIso())

  useEffect(() => {
    if (!open) return
    setFromId(buckets[0]?.id ?? "")
    setToId(buckets[1]?.id ?? "")
    setAmount("")
    setReason("")
    const today = todayIso()
    setOn(monthKeyFor(today, startDay) === monthKey ? today : monthWindow(monthKey, startDay).from)
  }, [open, buckets, monthKey, startDay])

  const freeOf = (id: string) => figures.buckets.find((b) => b.id === id)?.free ?? 0

  async function submit(event: FormEvent) {
    event.preventDefault()
    const from = buckets.find((b) => b.id === fromId)
    const to = buckets.find((b) => b.id === toId)
    const value = toMinorUnits(amount, currency)
    if (!from || !to || from.id === to.id || value <= 0) return
    await moveMoney(from, to, value, on, monthKey, reason.trim() || null, currency)
    toast(t("plan.moved", { amount: formatMoney(value, currency, i18n.language), from: from.name, to: to.name }))
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{t("plan.move")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="from" label={t("transactions.fromBucket")}>
            <Select value={fromId} onValueChange={setFromId}>
              <SelectTrigger id="from"><SelectValue /></SelectTrigger>
              <SelectContent>{buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name} · {formatMoney(freeOf(b.id), currency, i18n.language)}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <Field id="to" label={t("transactions.to")}>
            <Select value={toId} onValueChange={setToId}>
              <SelectTrigger id="to"><SelectValue /></SelectTrigger>
              <SelectContent>{buckets.filter((b) => b.id !== fromId).map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}</SelectContent>
            </Select>
          </Field>
          <Field id="amount" label={t("transactions.amount")}>
            <Input id="amount" inputMode="decimal" dir="ltr" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
          </Field>
          <Field id="on" label={t("transactions.date")}>
            <Input id="on" type="date" dir="ltr" value={on} onChange={(e) => setOn(e.target.value)} required />
          </Field>
          <Field id="reason" label={t("plan.reason")}>
            <Input id="reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={120} placeholder={t("plan.reasonHint")} />
          </Field>
          <p className="text-xs opacity-70">{t("plan.moveHint")}</p>
          <SheetFooter className="px-0"><Button type="submit" disabled={!fromId || !toId || fromId === toId}>{t("plan.moveConfirm")}</Button></SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
