import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { cancelDeletion, restoreAccount, revokeSessions, searchAccounts, suspendAccount, type Account } from "@/api/admin"
import { describeError } from "@/app/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { useAdminAction, useWhen } from "./adminAction"

export function AccountsTab() {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const [query, setQuery] = useState("")
  const [results, setResults] = useState<Account[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function search(event?: FormEvent) {
    event?.preventDefault()
    try {
      setResults(await searchAccounts(query))
      setError(null)
    } catch (e) {
      setError(describeError(e))
    }
  }

  const replace = (updated: Account) => setResults((all) => all?.map((a) => (a.id === updated.id ? updated : a)) ?? null)

  async function suspend(account: Account) {
    const reason = window.prompt(t("admin.accounts.suspendReason"))
    if (!reason?.trim()) return
    await run(async () => replace(await suspendAccount(account.id ?? "", reason.trim())), t("admin.accounts.suspended"))
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.accounts.title")}</CardTitle>
          <CardDescription>{t("admin.accounts.body")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={search} className="flex gap-2">
            <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("admin.accounts.search")} aria-label={t("admin.accounts.search")} />
            <Button type="submit" disabled={query.trim().length < 3}>{t("admin.accounts.find")}</Button>
          </form>
          {error && <p className="mt-2 text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>

      {results?.length === 0 && <p className="text-sm text-muted-foreground">{t("admin.accounts.none")}</p>}
      {results?.map((a) => (
        <Card key={a.id}>
          <CardHeader>
            <CardTitle className="flex flex-wrap items-center gap-2 text-base">
              {a.displayName}
              {a.operator && <Badge>{t("admin.accounts.operator")}</Badge>}
              {a.suspendedAt && <Badge variant="destructive">{t("admin.accounts.isSuspended")}</Badge>}
              {a.lockedUntil && <Badge variant="secondary">{t("admin.accounts.locked")}</Badge>}
              {a.deletionRequestedAt && <Badge variant="secondary">{t("admin.accounts.deleting")}</Badge>}
              {a.authScheme === "LEGACY" && <Badge variant="outline">{t("admin.accounts.legacy")}</Badge>}
            </CardTitle>
            <CardDescription dir="ltr" className="text-start">{a.email ?? a.phone}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <p className="text-muted-foreground">
              {t("admin.accounts.joined", { when: when(a.createdAt) })} · {a.verified ? t("admin.accounts.verified") : t("admin.accounts.unverified")}
            </p>
            <div className="flex flex-wrap gap-2">
              {a.suspendedAt
                ? <Button size="sm" variant="outline" onClick={() => void run(async () => replace(await restoreAccount(a.id ?? "")), t("admin.accounts.restored"))}>{t("admin.accounts.restore")}</Button>
                : <Button size="sm" variant="destructive" onClick={() => void suspend(a)}>{t("admin.accounts.suspend")}</Button>}
              <Button size="sm" variant="outline" onClick={() => void run(() => revokeSessions(a.id ?? ""), t("admin.accounts.signedOut"))}>{t("admin.accounts.signOut")}</Button>
              {a.deletionRequestedAt && (
                <Button size="sm" variant="outline" onClick={() => void run(async () => replace(await cancelDeletion(a.id ?? "")), t("admin.accounts.deletionCancelled"))}>
                  {t("admin.accounts.cancelDeletion")}
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
