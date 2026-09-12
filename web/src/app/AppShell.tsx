import { Banknote, House, RefreshCw, Settings } from "lucide-react"
import { useTranslation } from "react-i18next"
import { NavLink, Outlet } from "react-router"

import { useSession } from "@/api/auth"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { useSyncStatus } from "@/sync/engine"

export function AppShell() {
  const { t } = useTranslation()
  const session = useSession()

  const items = [
    { to: "/", label: t("nav.home"), icon: House, end: true },
    { to: "/accounts", label: t("nav.accounts"), icon: Banknote },
    { to: "/sync", label: t("nav.sync"), icon: RefreshCw },
    { to: "/settings", label: t("nav.settings"), icon: Settings },
  ]

  return (
    <div className="flex min-h-dvh flex-col md:flex-row">
      <aside className="hidden w-60 shrink-0 flex-col gap-2 border-e-4 border-border bg-secondary-background p-4 md:flex">
        <div className="mb-4 flex items-center justify-between">
          <span className="text-2xl font-heading">{t("app.name")}</span>
          <SyncBadge />
        </div>
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn(
                "flex items-center gap-3 rounded-base border-2 border-border px-3 py-2 font-heading",
                isActive ? "bg-main text-main-foreground shadow-shadow" : "hover:bg-background",
              )
            }
          >
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
        <p className="mt-auto truncate text-sm opacity-70">{session?.householdName}</p>
      </aside>

      <main className="flex-1 p-4 pb-24 md:p-8 md:pb-8">
        <div className="mb-4 flex items-center justify-between md:hidden">
          <span className="text-2xl font-heading">{t("app.name")}</span>
          <SyncBadge />
        </div>
        <Outlet />
      </main>

      <nav className="fixed inset-x-0 bottom-0 grid grid-cols-4 border-t-4 border-border bg-secondary-background md:hidden">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn("flex flex-col items-center gap-1 py-2 text-xs font-heading", isActive && "bg-main text-main-foreground")
            }
          >
            <item.icon className="size-5" />
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function SyncBadge() {
  const { t } = useTranslation()
  const status = useSyncStatus()
  const label =
    status.pending > 0
      ? t("sync.status.pending", { count: status.pending })
      : t(`sync.status.${status.phase}`)
  return (
    <Badge variant={status.phase === "error" ? "default" : "neutral"} className="max-w-40 truncate">
      {label}
    </Badge>
  )
}
