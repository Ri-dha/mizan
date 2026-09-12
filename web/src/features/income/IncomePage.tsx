import { Plus } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { useSelectedMonth } from "@/app/month"
import { MonthPicker } from "@/components/MonthPicker"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import type { IncomeReceipt, IncomeSource } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import { formatMoney } from "@/domain/money/format"
import { useMonthView } from "@/features/plan/useMonthFigures"
import { IncomeSourceSheet } from "./IncomeSourceSheet"
import { ReceiptSheet, type ReceiptDraft } from "./ReceiptSheet"

export function IncomePage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const base = session?.baseCurrency ?? "IQD"
  const [monthKey, setMonthKey] = useSelectedMonth(startDay)
  const view = useMonthView(monthKey, startDay)
  const [editingSource, setEditingSource] = useState<IncomeSource | null | "new">(null)
  const [receipt, setReceipt] = useState<ReceiptDraft | null>(null)
  const today = todayIso()
  const money = (amount: number, currency = base) => formatMoney(amount, currency, i18n.language)
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { day: "numeric", month: "short" }).format(new Date(iso))

  const irregular = view.receipts.filter((r) => !view.occurrences.some((o) => o.receipt?.id === r.id))

  function markReceived(source: IncomeSource, occurrenceDate: string) {
    setReceipt({
      existing: null,
      incomeSourceId: source.id,
      monthKey,
      receivedOn: occurrenceDate <= today ? occurrenceDate : today,
      amount: source.amount,
      currency: source.currency,
      fxRateMicros: source.fxRateMicros,
    })
  }

  function editReceipt(existing: IncomeReceipt) {
    setReceipt({ existing, incomeSourceId: existing.incomeSourceId, monthKey, receivedOn: existing.receivedOn, amount: existing.amount, currency: existing.currency, fxRateMicros: existing.fxRateMicros })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("income.title")}</h1>
        <MonthPicker value={monthKey} onChange={setMonthKey} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("income.thisMonth")}</CardTitle>
          <CardDescription>
            {t("income.planned")}: {money(view.income.planned)} · {t("income.received")}: {money(view.income.received)}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {view.occurrences.length === 0 && irregular.length === 0 && <p className="opacity-70">{t("income.nothingPlanned")}</p>}
          {view.occurrences.map((occurrence) => (
            <div key={`${occurrence.source.id}-${occurrence.date}`} className="flex items-center justify-between gap-3 rounded-base border-2 border-border p-3">
              <div className="flex flex-col">
                <span className="font-heading">{occurrence.source.name}</span>
                <span className="text-sm opacity-70">
                  {occurrence.receipt
                    ? t("income.receivedOn", { date: date(occurrence.receipt.receivedOn) })
                    : t("income.expectedOn", { date: date(occurrence.date) })}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className="tabular-nums font-heading">{money(occurrence.receipt?.baseAmount ?? occurrence.amount)}</span>
                {occurrence.receipt ? (
                  <Badge variant="neutral" className="cursor-pointer" onClick={() => editReceipt(occurrence.receipt!)}>{t("income.received")}</Badge>
                ) : (
                  <Button size="sm" variant={occurrence.date < today ? "default" : "neutral"} onClick={() => markReceived(occurrence.source, occurrence.date)}>
                    {occurrence.date < today ? t("income.overdue") : t("income.markReceived")}
                  </Button>
                )}
              </div>
            </div>
          ))}
          {irregular.map((r) => (
            <div key={r.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => editReceipt(r)}>
              <div className="flex flex-col">
                <span className="font-heading">{r.note || t("income.irregular")}</span>
                <span className="text-sm opacity-70">{t("income.receivedOn", { date: date(r.receivedOn) })}</span>
              </div>
              <span className="tabular-nums font-heading">{money(r.baseAmount)}</span>
            </div>
          ))}
          <Button variant="neutral" className="self-start" onClick={() => setReceipt({ existing: null, incomeSourceId: null, monthKey, receivedOn: today, amount: 0, currency: base, fxRateMicros: 1_000_000 })}>
            <Plus /> {t("income.addIrregular")}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>{t("income.sources")}</CardTitle>
          <Button size="sm" onClick={() => setEditingSource("new")}><Plus /> {t("income.addSource")}</Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {view.sources.length === 0 && <p className="opacity-70">{t("income.noSources")}</p>}
          {view.sources.map((source) => (
            <div key={source.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => setEditingSource(source)}>
              <div className="flex flex-col">
                <span className="font-heading">{source.name}</span>
                <span className="text-sm opacity-70">
                  {t(`income.frequencies.${source.frequency}`)}
                  {source.frequency === "MONTHLY" ? ` · ${t("income.payDayShort", { day: source.payDay })}` : ""}
                  {source.activeTo ? ` · ${t("income.endsOn", { date: date(source.activeTo) })}` : ""}
                </span>
              </div>
              <span className="tabular-nums font-heading">{money(source.amount, source.currency)}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <IncomeSourceSheet open={editingSource !== null} source={editingSource === "new" ? null : editingSource} onClose={() => setEditingSource(null)} />
      <ReceiptSheet draft={receipt} onClose={() => setReceipt(null)} />
    </div>
  )
}
