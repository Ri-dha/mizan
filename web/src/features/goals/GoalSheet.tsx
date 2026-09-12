import { useEffect, useState, type FormEvent } from "react"
import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { currentMonthKey } from "@/app/month"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { createGoal, updateGoal } from "@/db/goals"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import type { Goal } from "@/db/schema"
import { writeFields } from "@/db/write"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const CURRENCIES = ["IQD", "USD"]
const NONE = "__none__"

export function GoalSheet({ open, goal, onClose }: { open: boolean; goal: Goal | null; onClose: () => void }) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const buckets = useLiveQuery(async () => liveBucketsOf(planFor(await livePlans(), currentMonthKey(startDay))?.id), [startDay], [])
  const [name, setName] = useState("")
  const [target, setTarget] = useState("0")
  const [currency, setCurrency] = useState(base)
  const [targetDate, setTargetDate] = useState("")
  const [contribution, setContribution] = useState("")
  const [bucketId, setBucketId] = useState(NONE)
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(goal?.name ?? "")
    setTarget(goal ? fromMinorUnits(goal.targetAmount, goal.currency) : "0")
    setCurrency(goal?.currency ?? base)
    setTargetDate(goal?.targetDate ?? "")
    setContribution(goal?.monthlyContribution ? fromMinorUnits(goal.monthlyContribution, goal.currency) : "")
    setBucketId(goal?.bucketId ?? NONE)
    setIsPrivate(goal?.visibility === "PRIVATE")
  }, [open, goal, base])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      targetAmount: toMinorUnits(target, currency),
      currency,
      targetDate: targetDate || null,
      monthlyContribution: contribution ? toMinorUnits(contribution, currency) : null,
      bucketId: bucketId === NONE ? null : bucketId,
      note: null,
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (goal) await updateGoal(goal.id, input)
    else await createGoal(input)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader><SheetTitle>{goal ? t("goals.edit") : t("goals.add")}</SheetTitle></SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="name" label={t("income.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="target" label={t("goals.target")}>
              <Input id="target" inputMode="decimal" dir="ltr" value={target} onChange={(e) => setTarget(e.target.value)} required />
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>{CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}</SelectContent>
              </Select>
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field id="targetDate" label={t("goals.targetDate")}>
              <Input id="targetDate" type="date" dir="ltr" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
            </Field>
            <Field id="contribution" label={t("goals.monthly")}>
              <Input id="contribution" inputMode="decimal" dir="ltr" value={contribution} onChange={(e) => setContribution(e.target.value)} />
            </Field>
          </div>
          <p className="text-xs opacity-70">{t("goals.eitherHint")}</p>
          <Field id="bucket" label={t("debts.paidFromBucket")}>
            <Select value={bucketId} onValueChange={setBucketId}>
              <SelectTrigger id="bucket"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{t("bills.noBucket")}</SelectItem>
                {buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {goal && <Button type="button" variant="neutral" onClick={() => void writeFields<Goal>("goal", goal.id, { status: "ARCHIVED" }).then(onClose)}>{t("goals.archive")}</Button>}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
