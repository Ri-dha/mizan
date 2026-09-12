import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { useSession } from "@/api/auth"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { liveCashAccounts, totalsByCurrency } from "@/db/cashAccounts"
import { formatMoney } from "@/domain/money/format"

export function HomePage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const accounts = useLiveQuery(liveCashAccounts, [], [])
  const totals = totalsByCurrency(accounts)

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-3xl">{t("home.greeting", { name: session?.displayName })}</h1>

      {session && !session.verified && (
        <Alert>
          <AlertDescription className="flex items-center justify-between gap-2">
            <span>{t("auth.verifyTitle")}</span>
            <Button asChild size="sm"><Link to="/verify">{t("auth.verify")}</Link></Button>
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t("home.cashTotal")}</CardTitle>
          <CardDescription>{t("home.accounts", { count: accounts.length })}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {accounts.length === 0 ? (
            <>
              <p>{t("home.noAccounts")}</p>
              <Button asChild className="self-start"><Link to="/accounts">{t("home.addAccount")}</Link></Button>
            </>
          ) : (
            Object.entries(totals).map(([currency, total]) => (
              <p key={currency} className="text-3xl font-heading tabular-nums">{formatMoney(total, currency, i18n.language)}</p>
            ))
          )}
        </CardContent>
      </Card>

      <p className="text-sm opacity-70">{t("home.comingSoon")}</p>
    </div>
  )
}
