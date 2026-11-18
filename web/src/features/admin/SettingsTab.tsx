import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { fetchSettings, resetSetting, saveSetting, type Setting } from "@/api/admin"
import { describeError } from "@/app/errors"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useAdminAction, useWhen } from "./adminAction"

export function SettingsTab() {
  const { t } = useTranslation()
  const [settings, setSettings] = useState<Setting[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchSettings().then(setSettings).catch((e) => setError(describeError(e)))
  }, [])

  if (error) return <p className="text-destructive">{error}</p>
  if (!settings) return <p className="text-muted-foreground">{t("common.loading")}</p>
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">{t("admin.settings.body")}</p>
      {settings.map((s) => <SettingRow key={s.key} setting={s} onChange={setSettings} />)}
    </div>
  )
}

function SettingRow({ setting, onChange }: { setting: Setting; onChange: (all: Setting[]) => void }) {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const secret = setting.kind === "SECRET"
  const [draft, setDraft] = useState(secret ? "" : setting.value ?? "")
  const key = setting.key ?? ""

  async function save() {
    const ok = await run(async () => onChange(await saveSetting(key, draft)), t("admin.settings.saved"))
    if (ok && secret) setDraft("")
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          {t(`admin.settingNames.${key}`, { defaultValue: key })}
          {setting.overridden ? <Badge>{t("admin.settings.overridden")}</Badge> : <Badge variant="secondary">{t("admin.settings.default")}</Badge>}
        </CardTitle>
        <CardDescription>
          {t(`admin.settingHelp.${key}`, { defaultValue: "" })}{" "}
          {setting.overridden && t("admin.settings.changedBy", { when: when(setting.updatedAt) })}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <p className="text-sm text-muted-foreground">
          {t("admin.settings.current")}: <span className="font-mono text-foreground">{setting.value || "—"}</span>
          {" · "}{t("admin.settings.envDefault")}: <span className="font-mono">{setting.defaultValue || "—"}</span>
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {setting.kind === "BOOLEAN" || setting.kind === "CHOICE" ? (
            <Select value={draft} onValueChange={setDraft}>
              <SelectTrigger className="w-40" aria-label={key}><SelectValue /></SelectTrigger>
              <SelectContent>
                {(setting.kind === "BOOLEAN" ? ["true", "false"] : setting.choices ?? []).map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          ) : (
            <Input className="w-full sm:w-80" dir="ltr" type={secret ? "password" : "text"} autoComplete="off" value={draft}
              placeholder={secret ? t("admin.settings.newSecret") : ""} onChange={(e) => setDraft(e.target.value)} aria-label={key} />
          )}
          <Button size="sm" disabled={draft.trim() === ""} onClick={() => void save()}>{t("admin.settings.save")}</Button>
          {setting.overridden && (
            <Button size="sm" variant="outline" onClick={() => void run(async () => onChange(await resetSetting(key)), t("admin.settings.resetDone"))}>
              {t("admin.settings.reset")}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
