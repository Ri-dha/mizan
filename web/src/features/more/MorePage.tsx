import { Banknote, HandCoins, PiggyBank, Receipt, RefreshCw, Settings, Wallet } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { Card, CardContent } from "@/components/ui/card"

export const MORE_LINKS = [
  { to: "/income", key: "nav.income", icon: Wallet },
  { to: "/bills", key: "nav.bills", icon: Receipt },
  { to: "/accounts", key: "nav.accounts", icon: Banknote },
  { to: "/debts", key: "nav.debts", icon: HandCoins },
  { to: "/goals", key: "nav.goals", icon: PiggyBank },
  { to: "/sync", key: "nav.sync", icon: RefreshCw },
  { to: "/settings", key: "nav.settings", icon: Settings },
]

export function MorePage() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl">{t("nav.more")}</h1>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {MORE_LINKS.map((link) => (
          <Link key={link.to} to={link.to}>
            <Card className="h-full"><CardContent className="flex items-center gap-3 py-4 font-heading"><link.icon className="size-5" />{t(link.key)}</CardContent></Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
