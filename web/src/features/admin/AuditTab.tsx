import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { searchAudit, type AuditEntry } from "@/api/admin"
import { describeError } from "@/app/errors"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { useWhen } from "./adminAction"

const PAGE = 50

export function AuditTab() {
  const { t } = useTranslation()
  const when = useWhen()
  const [action, setAction] = useState("")
  const [entries, setEntries] = useState<AuditEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  async function load(more: boolean) {
    try {
      const before = more ? entries.at(-1)?.occurredAt : undefined
      const page = await searchAudit({ action: action.trim().toUpperCase() || undefined, before, limit: PAGE })
      setEntries(more ? [...entries, ...page] : page)
      setError(null)
    } catch (e) {
      setError(describeError(e))
    }
  }

  useEffect(() => {
    searchAudit({ limit: PAGE }).then(setEntries).catch((e) => setError(describeError(e)))
  }, [])

  return (
    <div className="flex flex-col gap-3">
      <form onSubmit={(e) => { e.preventDefault(); void load(false) }} className="flex gap-2">
        <Input dir="ltr" value={action} onChange={(e) => setAction(e.target.value)} placeholder={t("admin.audit.filter")} aria-label={t("admin.audit.filter")} />
        <Button type="submit" variant="outline">{t("admin.audit.apply")}</Button>
      </form>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <Card>
        <CardContent className="overflow-x-auto pt-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-muted-foreground">
                <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("prices.when")}</th>
                <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("admin.audit.action")}</th>
                <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("admin.audit.actor")}</th>
                <th className="px-2 py-1 text-start font-medium">{t("admin.audit.details")}</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id} className="border-t align-top">
                  <td className="whitespace-nowrap px-2 py-1.5">{when(e.occurredAt)}</td>
                  <td className="whitespace-nowrap px-2 py-1.5 font-mono text-xs" dir="ltr">{e.action}</td>
                  <td className="whitespace-nowrap px-2 py-1.5 font-mono text-xs text-muted-foreground" dir="ltr">{e.actorId?.slice(0, 8) ?? "—"}</td>
                  <td className="max-w-md break-all px-2 py-1.5 font-mono text-xs text-muted-foreground" dir="ltr">{[e.before, e.after].filter(Boolean).join(" → ")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
      {entries.length > 0 && entries.length % PAGE === 0 && <Button variant="outline" className="self-start" onClick={() => void load(true)}>{t("admin.audit.more")}</Button>}
    </div>
  )
}
