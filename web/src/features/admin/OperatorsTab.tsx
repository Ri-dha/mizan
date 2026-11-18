import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { fetchOperators, grantOperator, revokeOperator, type Operator } from "@/api/admin"
import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { useAdminAction, useWhen } from "./adminAction"

export function OperatorsTab() {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const session = useSession()
  const [operators, setOperators] = useState<Operator[]>([])
  const [identifier, setIdentifier] = useState("")

  useEffect(() => {
    fetchOperators().then(setOperators).catch(() => setOperators([]))
  }, [])

  async function grant() {
    const ok = await run(async () => setOperators(await grantOperator(identifier.trim())), t("admin.operators.granted"))
    if (ok) setIdentifier("")
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.operators.title")}</CardTitle>
          <CardDescription>{t("admin.operators.body")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-2">
          <Field id="op-identifier" label={t("auth.identifier")}>
            <Input id="op-identifier" dir="ltr" className="w-72" value={identifier} onChange={(e) => setIdentifier(e.target.value)} />
          </Field>
          <Button disabled={!identifier.trim()} onClick={() => void grant()}>{t("admin.operators.grant")}</Button>
        </CardContent>
      </Card>
      {operators.map((o) => (
        <Card key={o.id}>
          <CardContent className="flex flex-wrap items-center justify-between gap-2 pt-6 text-sm">
            <div>
              <p className="font-medium">{o.displayName}</p>
              <p className="text-muted-foreground" dir="ltr">{o.email ?? o.phone}</p>
              {o.elevatedUntil && <p className="text-xs text-muted-foreground">{t("admin.operators.elevatedUntil", { when: when(o.elevatedUntil) })}</p>}
            </div>
            {o.id !== session?.userId && (
              <Button size="sm" variant="outline" onClick={() => window.confirm(t("admin.operators.revokeConfirm")) && void run(async () => setOperators(await revokeOperator(o.id ?? "")), t("admin.operators.revoked"))}>
                {t("admin.operators.revoke")}
              </Button>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
