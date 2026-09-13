import { BarChart3, House, ListOrdered, MoreHorizontal, PieChart, PiggyBank, Plus, Receipt, Settings } from "lucide-react"
import { Suspense, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, NavLink, Outlet, useNavigate } from "react-router"
import { useEffect } from "react"

import { takePendingInvite } from "@/api/household"

import { useSession } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { MORE_LINKS } from "@/features/more/MorePage"
import { QuickAddSheet } from "@/features/transactions/QuickAddSheet"
import { TourProvider } from "@/tours/TourProvider"
import { cn } from "@/lib/utils"
import { useSyncStatus } from "@/sync/engine"

export function AppShell() {
  const { t } = useTranslation()
  const session = useSession()
  const [adding, setAdding] = useState(false)
  const navigate = useNavigate()
  const canWrite = session?.role !== "VIEWER" && session?.role !== "ADVISOR"
  useEffect(() => {
    const pending = takePendingInvite()
    if (pending) navigate(`/join/${pending}`)
  }, [navigate])

  const role = session?.role
  // §4.2: a dependent gets their own ledger and goals; an advisor gets the report and nothing else.
  const primary = role === "DEPENDENT"
    ? [
      { to: "/", label: t("nav.home"), icon: House, end: true },
      { to: "/transactions", label: t("nav.transactions"), icon: ListOrdered },
      { to: "/goals", label: t("nav.goals"), icon: PiggyBank },
      { to: "/settings", label: t("nav.settings"), icon: Settings },
    ]
    : role === "ADVISOR"
      ? [
        { to: "/advisor", label: t("advisor.nav"), icon: BarChart3, end: true },
        { to: "/household", label: t("household.title"), icon: House },
        { to: "/help", label: t("help.title"), icon: ListOrdered },
        { to: "/settings", label: t("nav.settings"), icon: Settings },
      ]
      : [
        { to: "/", label: t("nav.home"), icon: House, end: true },
        { to: "/transactions", label: t("nav.transactions"), icon: ListOrdered },
        { to: "/plan", label: t("nav.plan"), icon: PieChart },
        { to: "/bills", label: t("nav.bills"), icon: Receipt },
      ]
  const restricted = role === "DEPENDENT" || role === "ADVISOR"
  const sidebar = restricted ? primary : [...primary, ...MORE_LINKS.filter((l) => l.to !== "/bills").map((l) => ({ to: l.to, label: t(l.key), icon: l.icon, end: false }))]
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn("flex items-center gap-3 rounded-base border-2 border-border px-3 py-2 font-heading", isActive ? "bg-main text-main-foreground shadow-shadow" : "hover:bg-background")

  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="print:hidden hidden w-60 shrink-0 flex-col gap-2 border-e-4 border-border bg-secondary-background p-4 md:flex">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-2xl font-heading">{t("app.name")}</span>
          <SyncBadge />
        </div>
        {canWrite && <Button onClick={() => setAdding(true)} className="mb-2" data-tour="quick-add-desktop"><Plus /> {t("transactions.add")}</Button>}
        {sidebar.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        <p className="mt-auto truncate text-sm opacity-70">{session?.householdName}</p>
      </aside>

      <main className="flex-1 p-4 pb-28 md:p-8 md:pb-8">
        <div className="mb-4 flex items-center justify-between md:hidden">
          <span className="text-2xl font-heading">{t("app.name")}</span>
          <SyncBadge />
        </div>
        <Suspense fallback={<p className="opacity-70">{t("common.loading")}</p>}>
          <Outlet />
        </Suspense>
      </main>

      {canWrite && <Button
        data-tour="quick-add"
        className="print:hidden fixed bottom-20 end-4 z-20 size-14 rounded-full md:hidden"
        size="icon"
        aria-label={t("transactions.add")}
        onClick={() => setAdding(true)}
      >
        <Plus className="size-7" />
      </Button>}

      <nav className={cn("print:hidden fixed inset-x-0 bottom-0 z-10 grid border-t-4 border-border bg-secondary-background md:hidden", restricted ? "grid-cols-4" : "grid-cols-5")}>
        {primary.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end}
            className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-heading", isActive && "bg-main text-main-foreground")}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        {!restricted && (
          <NavLink to="/more" data-tour="nav-more" className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-heading", isActive && "bg-main text-main-foreground")}>
            <MoreHorizontal className="size-5" />
            {t("nav.more")}
          </NavLink>
        )}
      </nav>

      <QuickAddSheet open={adding} transaction={null} onClose={() => setAdding(false)} />
      <TourProvider />
    </div>
  )
}

function SyncBadge() {
  const { t } = useTranslation()
  const status = useSyncStatus()
  const label = status.pending > 0 ? t("sync.status.pending", { count: status.pending }) : t(`sync.status.${status.phase}`)
  return (
    <Link to="/sync">
      <Badge variant={status.phase === "error" ? "default" : "neutral"} className="max-w-40 truncate">{label}</Badge>
    </Link>
  )
}
