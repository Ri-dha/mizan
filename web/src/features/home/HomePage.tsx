import { useLiveQuery } from "dexie-react-hooks"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { useSession } from "@/api/auth"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { liveCashAccounts, totalsByCurrency } from "@/db/cashAccounts"
import { formatMoney } from "@/domain/money/format"
import { currentMonthKey } from "@/app/month"
import { useMonthView } from "@/features/plan/useMonthFigures"
import { useMetals } from "@/features/metals/useMetals"
import { useNetWorth } from "@/features/networth/useNetWorth"
import { HelpButton } from "@/components/HelpButton"
import { useScreenTour } from "@/tours/useTour"

export function HomePage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const accounts = useLiveQuery(liveCashAccounts, [], [])
  const totals = totalsByCurrency(accounts)
  const startDay = session?.monthStartDay ?? 1
  const base = session?.baseCurrency ?? "IQD"
  const month = useMonthView(currentMonthKey(startDay), startDay)
  const metals = useMetals()
  const worth = useNetWorth()
  useScreenTour("welcome", session !== undefined)
  const metalTotals = metals.lines.reduce((acc, l) => ({ value: acc.value + l.valueNow, gain: acc.gain + l.gain }), { value: 0, gain: 0 })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-3xl">{t("home.greeting", { name: session?.displayName })}</h1>
        <HelpButton tour="welcome" />
      </div>

      {session && !session.verified && (
        <Alert>
          <AlertDescription className="flex items-center justify-between gap-2">
            <span>{t("auth.verifyTitle")}</span>
            <Button asChild size="sm"><Link to="/verify">{t("auth.verify")}</Link></Button>
          </AlertDescription>
        </Alert>
      )}

      <Card data-tour="home-networth">
        <CardHeader>
          <CardTitle>{t("home.netWorth")}</CardTitle>
          <CardDescription>{t("networth.assets")}: {formatMoney(worth.current.totalAssets, base, i18n.language)} · {t("networth.liabilities")}: {formatMoney(worth.current.totalLiabilities, base, i18n.language)}</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-2">
          <span className={`text-3xl font-heading tabular-nums ${worth.current.netWorth < 0 ? "text-chart-2" : ""}`}>{formatMoney(worth.current.netWorth, base, i18n.language)}</span>
          <Button asChild variant="neutral"><Link to="/networth">{t("home.openNetWorth")}</Link></Button>
        </CardContent>
      </Card>

      <Card data-tour="home-plan">
        <CardHeader>
          <CardTitle>{t("home.planTitle")}</CardTitle>
          <CardDescription>
            {t("home.incomePlanned")}: {formatMoney(month.income.planned, base, i18n.language)} · {t("home.incomeReceived")}: {formatMoney(month.income.received, base, i18n.language)}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {month.figures.buckets.map((figure) => {
            const bucket = month.buckets.find((b) => b.id === figure.id)!
            return (
              <div key={figure.id} className="flex items-center justify-between gap-2">
                <span className="flex items-center gap-2">
                  <span className="size-3 rounded-base border-2 border-border" style={{ background: bucket.colour }} />
                  {bucket.name}
                </span>
                <span className="tabular-nums">{formatMoney(figure.free, base, i18n.language)} <span className="text-xs opacity-70">{t("home.free")}</span></span>
              </div>
            )
          })}
          <Button asChild variant="neutral" className="self-start"><Link to="/plan">{t("home.viewPlan")}</Link></Button>
        </CardContent>
      </Card>

      <Card data-tour="home-metals">
        <CardHeader>
          <CardTitle>{t("home.metalsTitle")}</CardTitle>
          <CardDescription>{metals.lines.length === 0 ? t("home.metalsNone") : t("metals.valueNow") + ": " + formatMoney(metalTotals.value, base, i18n.language)}</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-2">
          <span className={`text-2xl font-heading tabular-nums ${metalTotals.gain < 0 ? "text-chart-2" : ""}`}>{(metalTotals.gain > 0 ? "+" : "") + formatMoney(metalTotals.gain, base, i18n.language)}</span>
          <Button asChild variant="neutral"><Link to="/metals">{t("nav.metals")}</Link></Button>
        </CardContent>
      </Card>

      <Card data-tour="home-bills">
        <CardHeader>
          <CardTitle>{t("home.upcomingBills")}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          {month.bills.filter((b) => !b.occurrence).slice(0, 5).map((bill) => (
            <div key={`${bill.expense.id}-${bill.dueDate}`} className="flex items-center justify-between gap-2">
              <span className="truncate">{bill.expense.name} <span className="text-xs opacity-70">{bill.dueDate.slice(5)}</span></span>
              <span className="tabular-nums">{formatMoney(bill.expectedAmount, base, i18n.language)}</span>
            </div>
          ))}
          {month.bills.every((b) => b.occurrence) && <p className="opacity-70">{t("home.noUpcoming")}</p>}
          <Button asChild variant="neutral" className="self-start"><Link to="/bills">{t("nav.bills")}</Link></Button>
        </CardContent>
      </Card>

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
