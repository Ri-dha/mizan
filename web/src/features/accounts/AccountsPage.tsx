import { useLiveQuery } from "dexie-react-hooks"
import { Plus } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { liveCashAccounts } from "@/db/cashAccounts"
import type { CashAccount } from "@/db/schema"
import { restore, softDelete } from "@/db/write"
import { formatMoney } from "@/domain/money/format"
import { AccountSheet } from "./AccountSheet"

export function AccountsPage() {
  const { t, i18n } = useTranslation()
  const accounts = useLiveQuery(liveCashAccounts, [], [])
  const [editing, setEditing] = useState<CashAccount | null | "new">(null)

  async function remove(account: CashAccount) {
    await softDelete("cash_account", account.id)
    setEditing(null)
    toast(t("accounts.deleted"), { action: { label: t("accounts.restore"), onClick: () => void restore("cash_account", account.id) } })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl">{t("accounts.title")}</h1>
        <Button onClick={() => setEditing("new")}><Plus /> {t("accounts.add")}</Button>
      </div>

      {accounts.map((account) => (
        <Card key={account.id} className="cursor-pointer" onClick={() => setEditing(account)}>
          <CardContent className="flex items-center justify-between gap-4 py-4">
            <div className="flex flex-col">
              <span className="font-heading">{account.name}</span>
              <span className="text-sm opacity-70">
                {t(`accounts.kinds.${account.kind}`)}{account.institution ? ` · ${account.institution}` : ""}
              </span>
            </div>
            <div className="flex items-center gap-2">
              {account.visibility === "PRIVATE" && <Badge variant="neutral">{t("accounts.private")}</Badge>}
              <span className="text-xl font-heading tabular-nums">{formatMoney(account.balance, account.currency, i18n.language)}</span>
            </div>
          </CardContent>
        </Card>
      ))}

      {accounts.length === 0 && <p className="opacity-70">{t("home.noAccounts")}</p>}

      <AccountSheet
        open={editing !== null}
        account={editing === "new" ? null : editing}
        onClose={() => setEditing(null)}
        onDelete={remove}
      />
    </div>
  )
}
