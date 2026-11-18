import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { fetchOverview, pollNow, type Overview } from "@/api/admin"
import { describeError } from "@/app/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useAdminAction, useWhen } from "./adminAction"

const GROUPS = ["accounts", "households", "notifications", "storage"] as const

export function OverviewTab() {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchOverview().then(setData).catch((e) => setError(describeError(e)))
  }, [])

  if (error) return <p className="text-destructive">{error}</p>
  if (!data) return <p className="text-muted-foreground">{t("common.loading")}</p>
  const quota = data.iqWealth

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 md:grid-cols-2">
        {GROUPS.map((group) => (
          <Card key={group}>
            <CardHeader><CardTitle>{t(`admin.overview.${group}`)}</CardTitle></CardHeader>
            <CardContent className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              {Object.entries((data[group] ?? {}) as Record<string, unknown>).map(([key, value]) => (
                <div key={key} className="flex flex-col">
                  <span className="text-muted-foreground">{t(`admin.stats.${key}`, { defaultValue: key })}</span>
                  <span className="font-semibold tabular-nums">{typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value) ? when(value) : String(value ?? "—")}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("admin.feeds.title")}</CardTitle>
          <CardDescription>{t("admin.feeds.body")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="rounded-lg border p-3 text-sm">
            <p className="font-medium">IQWealth</p>
            {quota?.configured ? (
              <p className="text-muted-foreground">
                {t("admin.feeds.quota", { remaining: quota.remainingToday ?? "?", reserve: quota.reserve ?? 0 })}
                {" · "}{t("admin.feeds.lastPoll", { when: when(quota.lastPollAt), outcome: quota.lastOutcome ?? "—" })}
              </p>
            ) : <p className="text-muted-foreground">{t("admin.feeds.noKey")}</p>}
            {quota?.keyRefused && <Badge variant="destructive" className="mt-2">{t("admin.feeds.keyRefused")}</Badge>}
            {quota?.pausedUntilMidnight && <Badge variant="secondary" className="mt-2">{t("admin.feeds.paused")}</Badge>}
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-muted-foreground">
                  <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("admin.feeds.feed")}</th>
                  <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("admin.feeds.lastSuccess")}</th>
                  <th className="whitespace-nowrap px-2 py-1 text-start font-medium">{t("admin.feeds.lastFailure")}</th>
                </tr>
              </thead>
              <tbody>
                {(data.feeds ?? []).map((feed) => (
                  <tr key={feed.name} className="border-t">
                    <td className="whitespace-nowrap px-2 py-1.5 font-medium">{feed.name}</td>
                    <td className="whitespace-nowrap px-2 py-1.5">{when(feed.lastSuccessAt)}</td>
                    <td className="px-2 py-1.5">{feed.lastFailureAt ? <span className="text-destructive">{when(feed.lastFailureAt)} · {feed.lastError}</span> : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => void run(async () => setData(await pollNow("ALL")), t("admin.feeds.polled"))}>{t("admin.feeds.pollAll")}</Button>
            <Button variant="outline" disabled={!quota?.configured}
              onClick={() => window.confirm(t("admin.feeds.pollDollarConfirm")) && void run(async () => setData(await pollNow("DOLLAR")), t("admin.feeds.polled"))}>
              {t("admin.feeds.pollDollar")}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
