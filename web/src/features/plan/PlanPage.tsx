import { Plus, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { useSelectedMonth } from "@/app/month"
import { MonthPicker } from "@/components/MonthPicker"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { saveBuckets, type BucketInput } from "@/db/plan"
import { formatMoney } from "@/domain/money/format"
import { BASIS_POINTS } from "@/domain/money/split"
import { basisPointsToPercent, percentToBasisPoints, rebalance } from "@/domain/plan/figures"
import { useMonthView } from "./useMonthFigures"

const PALETTE = ["#88aaee", "#ffdc58", "#ff6b6b", "#a3e635", "#c4a1ff", "#fd9745", "#7fdbca"]

export function PlanPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const currency = session?.baseCurrency ?? "IQD"
  const [monthKey, setMonthKey] = useSelectedMonth(startDay)
  const view = useMonthView(monthKey, startDay)
  const [draft, setDraft] = useState<BucketInput[] | null>(null)

  useEffect(() => {
    setDraft(null)
  }, [monthKey, view.plan?.id])

  const editing = draft ?? view.buckets.map((b) => ({ id: b.id, name: b.name, colour: b.colour, shareBasisPoints: b.shareBasisPoints }))
  const totalShares = editing.reduce((sum, b) => sum + b.shareBasisPoints, 0)
  const gapShares = totalShares - BASIS_POINTS
  const gapAmount = Math.round((view.income.planned * gapShares) / BASIS_POINTS)
  const money = (amount: number) => formatMoney(amount, currency, i18n.language)

  function update(index: number, patch: Partial<BucketInput>) {
    setDraft(editing.map((b, i) => (i === index ? { ...b, ...patch } : b)))
  }

  async function save() {
    await saveBuckets(monthKey, editing.filter((b) => b.name.trim() !== ""))
    setDraft(null)
    toast(t("plan.saved"))
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("plan.title")}</h1>
        <MonthPicker value={monthKey} onChange={setMonthKey} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("plan.figures")}</CardTitle>
          <CardDescription>
            {t("income.planned")}: {money(view.income.planned)} · {t("income.received")}: {money(view.income.received)}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {view.figures.buckets.length === 0 && <p className="opacity-70">{t("plan.noBuckets")}</p>}
          {view.figures.buckets.map((figure) => {
            const bucket = view.buckets.find((b) => b.id === figure.id)!
            return (
              <div key={figure.id} className="grid grid-cols-4 gap-x-3 gap-y-2 rounded-base border-2 border-border p-3 md:grid-cols-[1.6fr_repeat(4,1fr)]">
                <div className="col-span-4 flex min-w-0 items-center gap-2 md:col-span-1">
                  <span className="size-4 shrink-0 rounded-base border-2 border-border" style={{ background: bucket.colour }} />
                  <span className="truncate font-heading">{bucket.name}</span>
                  <span className="shrink-0 text-sm opacity-70">{basisPointsToPercent(bucket.shareBasisPoints)}%</span>
                </div>
                <Figure label={t("plan.allocated")} value={money(figure.allocated)} hint={money(figure.plannedAllocated)} />
                <Figure label={t("plan.committed")} value={money(figure.committed)} />
                <Figure label={t("plan.spent")} value={money(figure.spent)} />
                <Figure label={t("plan.free")} value={money(figure.free)} strong />
              </div>
            )
          })}
          {view.figures.unallocatedPlanned !== 0 && (
            <p className="text-sm">{t("plan.unallocated", { amount: money(view.figures.unallocatedPlanned) })}</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("plan.buckets")}</CardTitle>
          <CardDescription>
            {view.plan ? t("plan.versionNote", { from: view.plan.effectiveFrom }) : t("plan.noPlan")}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {editing.map((bucket, index) => (
            <div key={bucket.id ?? index} className="grid grid-cols-[auto_1fr_6rem_auto] items-center gap-2">
              <input
                type="color"
                aria-label={t("plan.colour")}
                value={bucket.colour}
                onChange={(e) => update(index, { colour: e.target.value })}
                className="size-10 cursor-pointer rounded-base border-2 border-border bg-transparent p-0.5"
              />
              <Input aria-label={t("plan.name")} value={bucket.name} onChange={(e) => update(index, { name: e.target.value })} maxLength={80} />
              <div className="flex items-center gap-1" dir="ltr">
                <Input
                  aria-label={t("plan.share")}
                  type="number" step="0.1" min="0" max="100" inputMode="decimal"
                  value={basisPointsToPercent(bucket.shareBasisPoints)}
                  onChange={(e) => update(index, { shareBasisPoints: percentToBasisPoints(Number(e.target.value) || 0) })}
                />
                <span>%</span>
              </div>
              <Button variant="neutral" size="icon" aria-label={t("plan.remove")} onClick={() => setDraft(editing.filter((_, i) => i !== index))}>
                <Trash2 />
              </Button>
            </div>
          ))}

          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" onClick={() => setDraft([...editing, { name: "", colour: PALETTE[editing.length % PALETTE.length], shareBasisPoints: 0 }])}>
              <Plus /> {t("plan.addBucket")}
            </Button>
            {gapShares !== 0 && (
              <Button variant="neutral" onClick={() => setDraft(editing.map((b, i) => ({ ...b, shareBasisPoints: rebalance(editing.map((x) => x.shareBasisPoints))[i] })))}>
                {t("plan.rebalance")}
              </Button>
            )}
          </div>

          <Alert>
            <AlertDescription>
              {gapShares === 0
                ? t("plan.balanced")
                : gapShares > 0
                  ? t("plan.over", { percent: basisPointsToPercent(gapShares), amount: money(gapAmount) })
                  : t("plan.under", { percent: basisPointsToPercent(-gapShares), amount: money(-gapAmount) })}
            </AlertDescription>
          </Alert>

          <Button onClick={() => void save()} disabled={draft === null} className="self-start">{t("plan.save")}</Button>
        </CardContent>
      </Card>
    </div>
  )
}

function Figure({ label, value, hint, strong }: { label: string; value: string; hint?: string; strong?: boolean }) {
  return (
    <div className="flex flex-col">
      <span className="text-xs opacity-70">{label}</span>
      <span className={`tabular-nums ${strong ? "font-heading" : ""}`}>{value}</span>
      {hint && <span className="text-xs opacity-60 tabular-nums">{hint}</span>}
    </div>
  )
}
