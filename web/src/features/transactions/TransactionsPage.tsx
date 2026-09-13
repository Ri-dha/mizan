import { RotateCcw } from "lucide-react"
import { useState } from "react"
import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { useSelectedMonth } from "@/app/month"
import { MonthPicker } from "@/components/MonthPicker"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { LedgerTransaction, TransactionType } from "@/db/schema"
import { EMPTY_FILTER, filterTransactions, liveRecentlyDeleted, restoreTransaction, type TransactionFilter } from "@/db/transactions"
import { formatMoney, toMinorUnits } from "@/domain/money/format"
import { useMonthView } from "@/features/plan/useMonthFigures"
import { QuickAddSheet } from "./QuickAddSheet"
import { HelpButton } from "@/components/HelpButton"
import { useScreenTour } from "@/tours/useTour"

const ALL = "__all__"
const PAGE_SIZE = 100

export function TransactionsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const startDay = session?.monthStartDay ?? 1
  const base = session?.baseCurrency ?? "IQD"
  const [monthKey, setMonthKey] = useSelectedMonth(startDay)
  const view = useMonthView(monthKey, startDay)
  const deleted = useLiveQuery(liveRecentlyDeleted, [], [])
  const [filter, setFilter] = useState<TransactionFilter>(EMPTY_FILTER)
  const [editing, setEditing] = useState<LedgerTransaction | null | "new">(null)
  const [limit, setLimit] = useState(PAGE_SIZE)
  useScreenTour("ledger")

  const matching = filterTransactions(view.transactions, filter)
  const rows = matching.slice(0, limit)
  const money = (amount: number) => formatMoney(amount, base, i18n.language)
  const day = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { weekday: "short", day: "numeric", month: "short" }).format(new Date(iso))
  const bucketName = (id: string | null) => view.buckets.find((b) => b.id === id)?.name

  const byDay = rows.reduce<Record<string, LedgerTransaction[]>>((groups, row) => {
    ;(groups[row.occurredOn] ??= []).push(row)
    return groups
  }, {})

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl">{t("transactions.title")}</h1>
        <div className="flex items-center gap-2"><MonthPicker value={monthKey} onChange={setMonthKey} /><HelpButton tour="ledger" /></div>
      </div>

      <div className="grid gap-2 sm:grid-cols-[2fr_1fr_1fr_1fr_1fr]" data-tour="ledger-filters">
        <Input placeholder={t("transactions.search")} value={filter.text} onChange={(e) => setFilter({ ...filter, text: e.target.value })} />
        <Select value={filter.bucketId ?? ALL} onValueChange={(v) => setFilter({ ...filter, bucketId: v === ALL ? null : v })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t("transactions.allBuckets")}</SelectItem>
            {view.buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={filter.type ?? ALL} onValueChange={(v) => setFilter({ ...filter, type: v === ALL ? null : (v as TransactionType) })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t("transactions.allTypes")}</SelectItem>
            {(["EXPENSE", "TRANSFER", "INCOME"] as const).map((type) => <SelectItem key={type} value={type}>{t(`transactions.types.${type}`)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input placeholder={t("transactions.min")} inputMode="numeric" dir="ltr" onChange={(e) => setFilter({ ...filter, minAmount: e.target.value ? toMinorUnits(e.target.value, base) : null })} />
        <Input placeholder={t("transactions.max")} inputMode="numeric" dir="ltr" onChange={(e) => setFilter({ ...filter, maxAmount: e.target.value ? toMinorUnits(e.target.value, base) : null })} />
      </div>

      <div className="flex flex-col gap-4" data-tour="ledger-list">
      {matching.length === 0 && <p className="opacity-70">{t("transactions.none")}</p>}
      {Object.entries(byDay).map(([date, items]) => (
        <Card key={date}>
          <CardHeader className="py-3"><CardTitle className="text-base">{day(date)}</CardTitle></CardHeader>
          <CardContent className="flex flex-col gap-2">
            {items.map((row) => (
              <div key={row.id} className="flex cursor-pointer items-center justify-between gap-3 rounded-base border-2 border-border p-3" onClick={() => setEditing(row)}>
                <div className="flex min-w-0 flex-col">
                  <span className="truncate font-heading">{row.payee || row.category || t(`transactions.types.${row.type}`)}</span>
                  <span className="truncate text-sm opacity-70">
                    {[bucketName(row.bucketId), row.category, row.note].filter(Boolean).join(" · ")}
                  </span>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {row.type !== "EXPENSE" && <Badge variant="neutral">{t(`transactions.types.${row.type}`)}</Badge>}
                  {row.attachmentId && <Badge variant="neutral">📎</Badge>}
                  <span className="tabular-nums font-heading">{row.type === "INCOME" ? "+" : ""}{money(row.baseAmount)}</span>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      ))}
      </div>

      {matching.length > rows.length && (
        <Button variant="neutral" className="self-center" onClick={() => setLimit(limit + PAGE_SIZE)}>
          {t("transactions.loadMore", { shown: rows.length, total: matching.length })}
        </Button>
      )}

      {deleted.length > 0 && (
        <Card>
          <CardHeader className="py-3"><CardTitle className="text-base">{t("transactions.recentlyDeleted")}</CardTitle></CardHeader>
          <CardContent className="flex flex-col gap-2">
            {deleted.map((row) => (
              <div key={row.id} className="flex items-center justify-between gap-3 rounded-base border-2 border-dashed border-border p-3 opacity-80">
                <span className="truncate">{row.payee || row.category || t(`transactions.types.${row.type}`)} · {money(row.baseAmount)}</span>
                <Button size="sm" variant="neutral" onClick={() => void restoreTransaction(row.id)}><RotateCcw /> {t("accounts.restore")}</Button>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <QuickAddSheet open={editing !== null} transaction={editing === "new" ? null : editing} onClose={() => setEditing(null)} />
    </div>
  )
}
