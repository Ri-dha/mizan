import { BarChart3, ChartLine, House, ListOrdered, MoreHorizontal, PieChart, PiggyBank, Plus, Receipt, Settings, ShieldCheck } from "lucide-react"
import { Suspense, useState } from "react"
import { useTranslation } from "react-i18next"

import { RecoveryCodeDialog } from "@/features/vault/VaultScreens"
import { AnnouncementBanner } from "@/components/AnnouncementBanner"
import { Link, NavLink, Outlet, useNavigate } from "react-router"
import { useEffect } from "react"

import { takePendingInvite } from "@/api/household"
import { fetchLiveQuotes } from "@/market/store"

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

  // Prices are fetched on request only: once when the app loads, and when the user presses Refresh.
  useEffect(() => {
    if (navigator.onLine && session?.role !== "ADVISOR") void fetchLiveQuotes().catch(() => undefined)
  }, [session?.role])

  const role = session?.role
  // §4.2: a dependent gets their own ledger and goals; an advisor gets the report and nothing else.
  const primary = role === "DEPENDENT"
    ? [
      { to: "/", label: t("nav.home"), icon: House, end: true },
      { to: "/transactions", label: t("nav.transactions"), icon: ListOrdered },
      { to: "/goals", label: t("nav.goals"), icon: PiggyBank },
      { to: "/prices", label: t("nav.prices"), icon: ChartLine },
      { to: "/settings", label: t("nav.settings"), icon: Settings },
    ]
    : role === "ADVISOR"
      ? [
        { to: "/advisor", label: t("advisor.nav"), icon: BarChart3, end: true },
        { to: "/household", label: t("household.title"), icon: House },
        { to: "/help", label: t("help.title"), icon: ListOrdered },
        { to: "/prices", label: t("nav.prices"), icon: ChartLine },
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
    cn("flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
      isActive ? "bg-sidebar-accent text-sidebar-accent-foreground" : "text-sidebar-foreground/75 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground")

  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="print:hidden sticky top-0 hidden h-dvh w-60 shrink-0 flex-col gap-1 overflow-y-auto border-e bg-sidebar p-3 text-sidebar-foreground md:flex">
        <div className="mb-3 flex items-center justify-between px-1 pt-1">
          <span className="text-xl font-semibold">{t("app.name")}</span>
          <SyncBadge />
        </div>
        {canWrite && <Button onClick={() => setAdding(true)} className="mb-2" data-tour="quick-add-desktop"><Plus /> {t("transactions.add")}</Button>}
        {sidebar.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
            <item.icon className="size-4" />
            {item.label}
          </NavLink>
        ))}
        {session?.operator && (
          <NavLink to="/admin" className={linkClass}>
            <ShieldCheck className="size-4" />
            {t("admin.nav")}
          </NavLink>
        )}
        <p className="mt-auto truncate px-1 pt-3 text-sm text-muted-foreground">{session?.householdName}</p>
      </aside>

      <main className="flex-1 p-4 pb-28 md:p-8 md:pb-8">
        <div className="mb-4 flex items-center justify-between md:hidden">
          <span className="text-xl font-semibold">{t("app.name")}</span>
          <SyncBadge />
        </div>
        <AnnouncementBanner />
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

      <nav className={cn("print:hidden fixed inset-x-0 bottom-0 z-10 grid border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden", "grid-cols-5")}>
        {primary.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end}
            className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-medium", isActive ? "text-primary" : "text-muted-foreground")}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        {!restricted && (
          <NavLink to="/more" data-tour="nav-more" className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-medium", isActive ? "text-primary" : "text-muted-foreground")}>
            <MoreHorizontal className="size-5" />
            {t("nav.more")}
          </NavLink>
        )}
      </nav>

      <QuickAddSheet open={adding} transaction={null} onClose={() => setAdding(false)} />
      <TourProvider />
      <RecoveryCodeDialog />
    </div>
  )
}

function SyncBadge() {
  const { t } = useTranslation()
  const status = useSyncStatus()
  const label = status.pending > 0 ? t("sync.status.pending", { count: status.pending }) : t(`sync.status.${status.phase}`)
  return (
    <Link to="/sync">
      <Badge variant={status.phase === "error" ? "destructive" : "secondary"} className="max-w-40 truncate">{label}</Badge>
    </Link>
  )
}
