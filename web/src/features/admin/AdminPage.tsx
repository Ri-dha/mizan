import { ShieldCheck } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Navigate } from "react-router"

import { adminSession } from "@/api/admin"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AccountsTab } from "./AccountsTab"
import { AnnouncementsTab } from "./AnnouncementsTab"
import { AuditTab } from "./AuditTab"
import { OperatorsTab } from "./OperatorsTab"
import { OverviewTab } from "./OverviewTab"
import { PricesTab } from "./PricesTab"
import { SettingsTab } from "./SettingsTab"
import { AdminActionProvider } from "./AdminActionProvider"

const TABS = ["overview", "settings", "prices", "accounts", "announcements", "audit", "operators"] as const

/** ADR admin-1: running the service. Nothing on this page can show a household's records. */
export function AdminPage() {
  const { t } = useTranslation()
  const [allowed, setAllowed] = useState<boolean | null>(null)

  useEffect(() => {
    adminSession().then((s) => setAllowed(s.operator ?? false)).catch(() => setAllowed(false))
  }, [])

  if (allowed === null) return <p className="text-muted-foreground">{t("common.loading")}</p>
  if (!allowed) return <Navigate to="/" replace />

  return (
    <AdminActionProvider>
      <div className="flex flex-col gap-4">
        <h1 className="flex items-center gap-2 text-3xl"><ShieldCheck className="size-7" /> {t("admin.title")}</h1>
        <Alert><AlertDescription>{t("admin.privacy")}</AlertDescription></Alert>
        <Tabs defaultValue="overview">
          <div className="overflow-x-auto">
            <TabsList>
              {TABS.map((tab) => <TabsTrigger key={tab} value={tab}>{t(`admin.tabs.${tab}`)}</TabsTrigger>)}
            </TabsList>
          </div>
          <TabsContent value="overview"><OverviewTab /></TabsContent>
          <TabsContent value="settings"><SettingsTab /></TabsContent>
          <TabsContent value="prices"><PricesTab /></TabsContent>
          <TabsContent value="accounts"><AccountsTab /></TabsContent>
          <TabsContent value="announcements"><AnnouncementsTab /></TabsContent>
          <TabsContent value="audit"><AuditTab /></TabsContent>
          <TabsContent value="operators"><OperatorsTab /></TabsContent>
        </Tabs>
      </div>
    </AdminActionProvider>
  )
}
