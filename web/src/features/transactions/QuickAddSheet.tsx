import { Camera, X } from "lucide-react"
import { useEffect, useRef, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { attachImage, attachmentObjectUrl, removeAttachment } from "@/attachments/store"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { liveCashAccounts } from "@/db/cashAccounts"
import { liveDebts } from "@/db/debts"
import { liveGoals } from "@/db/goals"
import { RATE_SCALE } from "@/db/income"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import type { Bucket, CounterpartyType, LedgerTransaction, TransactionType } from "@/db/schema"
import { createTransaction, deleteTransaction, knownCategories, liveTransactionsFor, updateTransaction } from "@/db/transactions"
import { monthKeyFor, todayIso } from "@/domain/calendar/month"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"
import { cn } from "@/lib/utils"
import { useLiveQuery } from "dexie-react-hooks"

const CURRENCIES = ["IQD", "USD"]
const TYPES: TransactionType[] = ["EXPENSE", "TRANSFER", "INCOME"]

interface Props {
  open: boolean
  transaction: LedgerTransaction | null
  onClose: () => void
}

/**
 * FR-TRX-01: amount, a bucket chip and Save are the only required taps; the date defaults to
 * today and everything else is optional. Doubles as the editor for an existing row.
 */
export function QuickAddSheet({ open, transaction, onClose }: Props) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const [type, setType] = useState<TransactionType>("EXPENSE")
  const [amount, setAmount] = useState("")
  const [currency, setCurrency] = useState(base)
  const [rate, setRate] = useState("1")
  const [occurredOn, setOccurredOn] = useState(todayIso())
  const [bucketId, setBucketId] = useState<string | null>(null)
  const [counterparty, setCounterparty] = useState<string>("")
  const [payee, setPayee] = useState("")
  const [category, setCategory] = useState("")
  const [note, setNote] = useState("")
  const [isPrivate, setIsPrivate] = useState(false)
  const [attachmentId, setAttachmentId] = useState<string | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const buckets = useLiveQuery(async () => liveBucketsOf(planFor(await livePlans(), monthKeyFor(occurredOn, startDay))?.id), [occurredOn, startDay], [] as Bucket[])
  const goals = useLiveQuery(liveGoals, [], [])
  const debts = useLiveQuery(liveDebts, [], [])
  const cashAccounts = useLiveQuery(liveCashAccounts, [], [])
  const categories = useLiveQuery(async () => knownCategories(await liveTransactionsFor(monthKeyFor(occurredOn, startDay))), [occurredOn, startDay], [] as string[])

  useEffect(() => {
    if (!open) return
    setType(transaction?.type ?? "EXPENSE")
    setAmount(transaction ? fromMinorUnits(transaction.amount, transaction.currency) : "")
    setCurrency(transaction?.currency ?? base)
    setRate(transaction ? String(transaction.fxRateMicros / RATE_SCALE) : "1")
    setOccurredOn(transaction?.occurredOn ?? todayIso())
    setBucketId(transaction?.bucketId ?? null)
    setCounterparty(transaction?.counterpartyType && transaction.counterpartyId ? `${transaction.counterpartyType}:${transaction.counterpartyId}` : "")
    setPayee(transaction?.payee ?? "")
    setCategory(transaction?.category ?? "")
    setNote(transaction?.note ?? "")
    setIsPrivate(transaction?.visibility === "PRIVATE")
    setAttachmentId(transaction?.attachmentId ?? null)
  }, [open, transaction, base])

  useEffect(() => {
    let revoked: string | null = null
    if (attachmentId) void attachmentObjectUrl(attachmentId).then((url) => { revoked = url; setPreview(url) })
    else setPreview(null)
    return () => { if (revoked) URL.revokeObjectURL(revoked) }
  }, [attachmentId])

  const needsRate = currency !== base
  const [counterpartyType, counterpartyId] = counterparty ? (counterparty.split(":") as [CounterpartyType, string]) : [null, null]

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      type,
      occurredOn,
      monthKey: monthKeyFor(occurredOn, startDay),
      amount: toMinorUnits(amount, currency),
      currency,
      fxRateMicros: needsRate ? Math.round(Number(rate) * RATE_SCALE) : RATE_SCALE,
      bucketId: type === "INCOME" ? null : bucketId,
      category: category.trim() || null,
      payee: payee.trim() || null,
      note: note.trim() || null,
      counterpartyType: type === "TRANSFER" ? counterpartyType : null,
      counterpartyId: type === "TRANSFER" ? counterpartyId : null,
      attachmentId,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (transaction) await updateTransaction(transaction.id, input)
    else await createTransaction(input)
    onClose()
  }

  async function pickPhoto(file: File | undefined) {
    if (!file) return
    const id = await attachImage(file, "TRANSACTION", transaction?.id ?? crypto.randomUUID())
    if (attachmentId) await removeAttachment(attachmentId)
    setAttachmentId(id)
  }

  async function remove() {
    if (!transaction) return
    await deleteTransaction(transaction.id)
    toast(t("transactions.deleted"), { action: { label: t("accounts.restore"), onClick: () => void import("@/db/transactions").then((m) => m.restoreTransaction(transaction.id)) } })
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{transaction ? t("transactions.edit") : t("transactions.add")}</SheetTitle>
        </SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <div className="grid grid-cols-3 gap-2">
            {TYPES.map((choice) => (
              <Button key={choice} type="button" variant={type === choice ? "default" : "neutral"} size="sm" onClick={() => setType(choice)}>
                {t(`transactions.types.${choice}`)}
              </Button>
            ))}
          </div>

          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="amount" label={t("transactions.amount")}>
              <Input id="amount" inputMode="decimal" dir="ltr" autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} required className="h-14 text-2xl font-heading" />
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="h-14 w-24"><SelectValue /></SelectTrigger>
                <SelectContent>{CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          {needsRate && (
            <Field id="rate" label={t("income.fxRate", { from: currency, to: base })}>
              <Input id="rate" inputMode="decimal" dir="ltr" value={rate} onChange={(e) => setRate(e.target.value)} required />
            </Field>
          )}

          {type !== "INCOME" && (
            <div className="flex flex-col gap-1.5">
              <Label>{type === "TRANSFER" ? t("transactions.fromBucket") : t("transactions.bucket")}</Label>
              <div className="flex flex-wrap gap-2">
                {buckets.map((bucket) => (
                  <button
                    key={bucket.id}
                    type="button"
                    onClick={() => setBucketId(bucket.id)}
                    className={cn(
                      "flex items-center gap-2 rounded-base border-2 border-border px-3 py-1.5 text-sm font-heading",
                      bucketId === bucket.id ? "bg-main text-main-foreground shadow-shadow" : "bg-secondary-background",
                    )}
                  >
                    <span className="size-3 rounded-base border border-border" style={{ background: bucket.colour }} />
                    {bucket.name}
                  </button>
                ))}
                {buckets.length === 0 && <span className="text-sm opacity-70">{t("plan.noBuckets")}</span>}
              </div>
            </div>
          )}

          {type === "TRANSFER" && (
            <Field id="counterparty" label={t("transactions.to")}>
              <Select value={counterparty} onValueChange={setCounterparty}>
                <SelectTrigger id="counterparty"><SelectValue placeholder={t("transactions.choose")} /></SelectTrigger>
                <SelectContent>
                  {buckets.filter((b) => b.id !== bucketId).map((b) => <SelectItem key={b.id} value={`BUCKET:${b.id}`}>{t("transactions.bucket")}: {b.name}</SelectItem>)}
                  {goals.map((g) => <SelectItem key={g.id} value={`GOAL:${g.id}`}>{t("nav.goals")}: {g.name}</SelectItem>)}
                  {debts.map((d) => <SelectItem key={d.id} value={`DEBT:${d.id}`}>{t("nav.debts")}: {d.name}</SelectItem>)}
                  {cashAccounts.map((c) => <SelectItem key={c.id} value={`CASH_ACCOUNT:${c.id}`}>{t("nav.accounts")}: {c.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          )}

          <Field id="occurredOn" label={t("transactions.date")}>
            <Input id="occurredOn" type="date" dir="ltr" value={occurredOn} onChange={(e) => setOccurredOn(e.target.value)} required />
          </Field>
          <Field id="payee" label={t("transactions.payee")}>
            <Input id="payee" value={payee} onChange={(e) => setPayee(e.target.value)} maxLength={80} />
          </Field>
          <Field id="category" label={t("transactions.category")}>
            <Input id="category" list="categories" value={category} onChange={(e) => setCategory(e.target.value)} maxLength={40} />
            <datalist id="categories">{categories.map((c) => <option key={c} value={c} />)}</datalist>
          </Field>
          <Field id="note" label={t("income.note")}>
            <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} />
          </Field>

          <div className="flex flex-col gap-2">
            <Label>{t("transactions.receipt")}</Label>
            <input ref={fileInput} type="file" accept="image/*" capture="environment" hidden onChange={(e) => void pickPhoto(e.target.files?.[0])} />
            {preview ? (
              <div className="relative w-fit">
                <img src={preview} alt="" className="max-h-40 rounded-base border-2 border-border" />
                <Button type="button" size="icon" variant="neutral" className="absolute -end-2 -top-2 size-7" onClick={() => { if (attachmentId) void removeAttachment(attachmentId); setAttachmentId(null) }}>
                  <X />
                </Button>
              </div>
            ) : (
              <Button type="button" variant="neutral" className="w-fit" onClick={() => fileInput.current?.click()}>
                <Camera /> {t("transactions.addReceipt")}
              </Button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>

          <SheetFooter className="flex-row justify-between px-0">
            {transaction && <Button type="button" variant="neutral" onClick={() => void remove()}>{t("accounts.delete")}</Button>}
            <Button type="submit" disabled={!amount || (type !== "INCOME" && !bucketId && buckets.length > 0)}>{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
