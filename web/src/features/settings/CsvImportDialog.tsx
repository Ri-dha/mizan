import { useLiveQuery } from "dexie-react-hooks"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { currentMonthKey } from "@/app/month"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { guessMapping, guessPositiveIs, mapRows, parseCsv, type CsvField, type CsvTable, type ImportedRow } from "@/data/csvImport"
import { liveBucketsOf, livePlans, planFor } from "@/db/plan"
import { db, type TransactionType } from "@/db/schema"
import { createTransaction } from "@/db/transactions"
import { monthKeyFor } from "@/domain/calendar/month"
import { toMinorUnits } from "@/domain/money/format"

const FIELDS: CsvField[] = ["ignore", "date", "amount", "type", "payee", "category", "note"]
const NONE = "__none__"
const PREVIEW_ROWS = 8

/** FR-DAT-04: pick the file, confirm which column is which, see the rows and duplicates, then write. */
export function CsvImportDialog({ file, onClose }: { file: File | null; onClose: () => void }) {
  const { t } = useTranslation()
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const startDay = session?.monthStartDay ?? 1
  const [table, setTable] = useState<CsvTable | null>(null)
  const [mapping, setMapping] = useState<CsvField[]>([])
  const [positiveIs, setPositiveIs] = useState<TransactionType>("EXPENSE")
  const [bucketId, setBucketId] = useState(NONE)
  const [skipDuplicates, setSkipDuplicates] = useState(true)
  const [busy, setBusy] = useState(false)
  const buckets = useLiveQuery(async () => liveBucketsOf(planFor(await livePlans(), currentMonthKey(startDay))?.id), [startDay], [])
  const existing = useLiveQuery(() => db.transactions.filter((x) => x.deletedAt === null).toArray(), [], [])

  if (file && !table) {
    void file.text().then((text) => {
      const parsed = parseCsv(text)
      const guessed = guessMapping(parsed.headers)
      setTable(parsed)
      setMapping(guessed)
      setPositiveIs(guessPositiveIs(parsed, guessed))
    })
  }

  const rows: ImportedRow[] = table ? mapRows(table, mapping, existing, (v) => toMinorUnits(String(v), base), positiveIs) : []
  const ready = mapping.includes("date") && mapping.includes("amount")
  const importable = rows.filter((r) => r.error === null && !(skipDuplicates && r.duplicate))

  async function commit() {
    setBusy(true)
    try {
      await db.transaction("rw", db.tables, async () => {
        for (const row of importable) {
          await createTransaction({
            type: row.type, occurredOn: row.occurredOn, monthKey: monthKeyFor(row.occurredOn, startDay), amount: row.amount, currency: base, fxRateMicros: 1_000_000,
            bucketId: row.type === "INCOME" ? null : bucketId === NONE ? null : bucketId, category: row.category, payee: row.payee, note: row.note,
            counterpartyType: null, counterpartyId: null, attachmentId: null, visibility: "SHARED",
          })
        }
      })
      toast(t("csv.imported", { count: importable.length }))
      close()
    } finally {
      setBusy(false)
    }
  }

  function close() {
    setTable(null)
    setMapping([])
    onClose()
  }

  return (
    <Dialog open={file !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("csv.title")}</DialogTitle>
          <DialogDescription>{t("csv.body")}</DialogDescription>
        </DialogHeader>
        {table && (
          <div className="flex flex-col gap-4">
            <div className="grid gap-2 sm:grid-cols-2">
              {table.headers.map((header, i) => (
                <Field key={i} id={`col-${i}`} label={header || t("csv.column", { n: i + 1 })}>
                  <Select value={mapping[i]} onValueChange={(v) => setMapping(mapping.map((m, j) => (j === i ? (v as CsvField) : m)))}>
                    <SelectTrigger id={`col-${i}`}><SelectValue /></SelectTrigger>
                    <SelectContent>{FIELDS.map((f) => <SelectItem key={f} value={f}>{t(`csv.fields.${f}`)}</SelectItem>)}</SelectContent>
                  </Select>
                </Field>
              ))}
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <Field id="positiveIs" label={t("csv.positiveIs")}>
                <Select value={positiveIs} onValueChange={(v) => setPositiveIs(v as TransactionType)}>
                  <SelectTrigger id="positiveIs"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EXPENSE">{t("transactions.types.EXPENSE")}</SelectItem>
                    <SelectItem value="INCOME">{t("transactions.types.INCOME")}</SelectItem>
                  </SelectContent>
                </Select>
              </Field>
              <Field id="bucket" label={t("csv.bucket")}>
                <Select value={bucketId} onValueChange={setBucketId}>
                  <SelectTrigger id="bucket"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>{t("bills.noBucket")}</SelectItem>
                    {buckets.map((b) => <SelectItem key={b.id} value={b.id}>{b.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <div className="overflow-x-auto rounded-base border-2 border-border">
              <table className="w-full text-xs">
                <thead><tr className="opacity-70"><th className="p-1 text-start">{t("transactions.date")}</th><th className="p-1 text-start">{t("transactions.types.EXPENSE")}/{t("transactions.types.INCOME")}</th><th className="p-1 text-end">{t("transactions.amount")}</th><th className="p-1 text-start">{t("transactions.payee")}</th><th className="p-1 text-start">{t("csv.status")}</th></tr></thead>
                <tbody>
                  {rows.slice(0, PREVIEW_ROWS).map((r) => (
                    <tr key={r.index} className={r.error ? "text-chart-2" : r.duplicate ? "opacity-60" : ""}>
                      <td className="p-1">{r.occurredOn || "?"}</td><td className="p-1">{t(`transactions.types.${r.type}`)}</td><td className="p-1 text-end tabular-nums">{r.amount}</td><td className="p-1">{r.payee ?? ""}</td>
                      <td className="p-1">{r.error ? t(`csv.errors.${r.error}`) : r.duplicate ? t("csv.duplicate") : t("csv.ok")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="text-sm">{t("csv.summary", { total: rows.length, ok: importable.length, duplicates: rows.filter((r) => r.duplicate).length, errors: rows.filter((r) => r.error).length })}</p>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={skipDuplicates} onChange={(e) => setSkipDuplicates(e.target.checked)} />{t("csv.skipDuplicates")}</label>
            <DialogFooter>
              <Button variant="neutral" type="button" onClick={close}>{t("settings.cancel")}</Button>
              <Button type="button" disabled={!ready || importable.length === 0 || busy} onClick={() => void commit()}>{t("csv.import", { count: importable.length })}</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
