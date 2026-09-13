import { House, ListOrdered, MoreHorizontal, PieChart, Plus, Receipt } from "lucide-react"
import { Suspense, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, NavLink, Outlet } from "react-router"

import { useSession } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { MORE_LINKS } from "@/features/more/MorePage"
import { QuickAddSheet } from "@/features/transactions/QuickAddSheet"
import { cn } from "@/lib/utils"
import { useSyncStatus } from "@/sync/engine"

export function AppShell() {
  const { t } = useTranslation()
  const session = useSession()
  const [adding, setAdding] = useState(false)

  const primary = [
    { to: "/", label: t("nav.home"), icon: House, end: true },
    { to: "/transactions", label: t("nav.transactions"), icon: ListOrdered },
    { to: "/plan", label: t("nav.plan"), icon: PieChart },
    { to: "/bills", label: t("nav.bills"), icon: Receipt },
  ]
  const sidebar = [...primary, ...MORE_LINKS.filter((l) => l.to !== "/bills").map((l) => ({ to: l.to, label: t(l.key), icon: l.icon, end: false }))]
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn("flex items-center gap-3 rounded-base border-2 border-border px-3 py-2 font-heading", isActive ? "bg-main text-main-foreground shadow-shadow" : "hover:bg-background")

  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="hidden w-60 shrink-0 flex-col gap-2 border-e-4 border-border bg-secondary-background p-4 md:flex">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-2xl font-heading">{t("app.name")}</span>
          <SyncBadge />
        </div>
        <Button onClick={() => setAdding(true)} className="mb-2"><Plus /> {t("transactions.add")}</Button>
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

      <Button
        className="fixed bottom-20 end-4 z-20 size-14 rounded-full md:hidden"
        size="icon"
        aria-label={t("transactions.add")}
        onClick={() => setAdding(true)}
      >
        <Plus className="size-7" />
      </Button>

      <nav className="fixed inset-x-0 bottom-0 z-10 grid grid-cols-5 border-t-4 border-border bg-secondary-background md:hidden">
        {primary.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end}
            className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-heading", isActive && "bg-main text-main-foreground")}>
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        <NavLink to="/more" className={({ isActive }) => cn("flex flex-col items-center gap-1 py-2 text-xs font-heading", isActive && "bg-main text-main-foreground")}>
          <MoreHorizontal className="size-5" />
          {t("nav.more")}
        </NavLink>
      </nav>

      <QuickAddSheet open={adding} transaction={null} onClose={() => setAdding(false)} />
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
