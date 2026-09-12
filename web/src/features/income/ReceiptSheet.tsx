import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { RATE_SCALE, recordReceipt, toBaseAmount } from "@/db/income"
import type { IncomeReceipt } from "@/db/schema"
import { softDelete, writeFields } from "@/db/write"
import { formatMoney, fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const CURRENCIES = ["IQD", "USD"]

export interface ReceiptDraft {
  existing: IncomeReceipt | null
  incomeSourceId: string | null
  monthKey: string
  receivedOn: string
  amount: number
  currency: string
  fxRateMicros: number
}

export function ReceiptSheet({ draft, onClose }: { draft: ReceiptDraft | null; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const [amount, setAmount] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [receivedOn, setReceivedOn] = useState("")
  const [note, setNote] = useState("")

  useEffect(() => {
    if (!draft) return
    setAmount(fromMinorUnits(draft.amount, draft.currency))
    setCurrency(draft.currency)
    setRate(String(draft.fxRateMicros / RATE_SCALE))
    setReceivedOn(draft.receivedOn)
    setNote(draft.existing?.note ?? "")
  }, [draft])

  const needsRate = currency !== base
  const fxRateMicros = needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE
  const minor = toMinorUnits(amount, currency)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!draft) return
    const fields = { receivedOn, amount: minor, currency, fxRateMicros, note: note.trim() || null }
    if (draft.existing) {
      await writeFields<IncomeReceipt>("income_receipt", draft.existing.id, { ...fields, baseAmount: toBaseAmount(minor, fxRateMicros) })
    } else {
      await recordReceipt({ ...fields, incomeSourceId: draft.incomeSourceId, monthKey: draft.monthKey, visibility: "SHARED" })
    }
    onClose()
  }

  async function remove() {
    if (draft?.existing) await softDelete("income_receipt", draft.existing.id)
    onClose()
  }

  return (
    <Sheet open={draft !== null} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{draft?.incomeSourceId ? t("income.markReceived") : t("income.addIrregular")}</SheetTitle>
        </SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
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
            <Field id="rate" label={t("income.fxRate", { from: currency, to: base })} hint={`= ${formatMoney(toBaseAmount(minor, fxRateMicros), base, i18n.language)}`}>
              <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
            </Field>
          )}
          <Field id="receivedOn" label={t("income.receivedOnLabel")}>
            <Input id="receivedOn" type="date" dir="ltr" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} required />
          </Field>
          <Field id="note" label={t("income.note")}>
            <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={120} />
          </Field>
          <SheetFooter className="flex-row justify-between px-0">
            {draft?.existing && <Button type="button" variant="neutral" onClick={() => void remove()}>{t("accounts.delete")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
