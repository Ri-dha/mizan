import { useLiveQuery } from "dexie-react-hooks"
import { RefreshCw } from "lucide-react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { db } from "@/db/schema"
import { syncNow, useSyncStatus } from "@/sync/engine"

export function SyncPage() {
  const { t, i18n } = useTranslation()
  const status = useSyncStatus()
  const conflicts = useLiveQuery(() => db.conflicts.filter((c) => !c.dismissed).reverse().sortBy("detectedAt"), [], [])

  const lastSync = status.lastSyncAt
    ? t("sync.lastSync", { when: new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "short" }).format(status.lastSyncAt) })
    : t("sync.never")

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl">{t("sync.title")}</h1>

      <Card>
        <CardHeader>
          <CardTitle>{status.pending > 0 ? t("sync.status.pending", { count: status.pending }) : t(`sync.status.${status.phase}`)}</CardTitle>
          <CardDescription>{lastSync}{status.lastError ? ` · ${status.lastError}` : ""}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={() => void syncNow()} disabled={status.phase === "syncing"}>
            <RefreshCw className={status.phase === "syncing" ? "animate-spin" : ""} /> {t("sync.syncNow")}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("sync.conflictsTitle")}</CardTitle>
          <CardDescription>{t("sync.conflictsBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {conflicts.length === 0 && <p className="opacity-70">{t("sync.noConflicts")}</p>}
          {conflicts.map((conflict) => (
            <div key={conflict.id} className="flex items-start justify-between gap-3 rounded-base border-2 border-border p-3">
              <div className="text-sm">
                <p className="font-heading">{conflict.table} · {conflict.field}</p>
                <p>{t("sync.yours")}: <code>{JSON.stringify(conflict.clientValue)}</code></p>
                <p>{t("sync.kept")}: <code>{JSON.stringify(conflict.serverValue)}</code></p>
              </div>
              <Button size="sm" variant="neutral" onClick={() => void db.conflicts.update(conflict.id, { dismissed: true })}>
                {t("sync.dismiss")}
              </Button>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
