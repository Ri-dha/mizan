import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { toast } from "sonner"

import { logout, updateHousehold, useSession } from "@/api/auth"
import { describeError } from "@/app/errors"
import { setTheme, useTheme, type Theme } from "@/app/theme"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { META_KEYS, readMeta, writeMeta } from "@/db/meta"
import { SUPPORTED_LOCALES } from "@/i18n"
import { clearLock, DEFAULT_LOCK_AFTER_SECONDS, removePin, lock, useLockState } from "@/lock/store"
import { canPromptInstall, promptInstall } from "@/pwa/register"
import { resetSyncState } from "@/sync/engine"

const LOCK_CHOICES = [0, 30, 60, 300]
const THEMES: Theme[] = ["system", "light", "dark"]

export function SettingsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const theme = useTheme()
  const lockState = useLockState()
  const [lockAfter, setLockAfter] = useState(DEFAULT_LOCK_AFTER_SECONDS)
  const [householdName, setHouseholdName] = useState(session?.householdName ?? "")
  const [monthStartDay, setMonthStartDay] = useState(session?.monthStartDay ?? 1)

  useEffect(() => {
    void readMeta<number>(META_KEYS.lockAfterSeconds).then((value) => value !== undefined && setLockAfter(value))
  }, [])

  async function saveHousehold(event: FormEvent) {
    event.preventDefault()
    try {
      await updateHousehold({ name: householdName, monthStartDay })
      toast(t("settings.saved"))
    } catch (e) {
      toast(describeError(e))
    }
  }

  async function signOut() {
    await clearLock()
    await resetSyncState()
    await logout()
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl">{t("settings.title")}</h1>

      <Card>
        <CardHeader><CardTitle>{t("settings.language")} · {t("settings.theme")}</CardTitle></CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field id="language" label={t("settings.language")}>
            <Select value={i18n.language.startsWith("ar") ? "ar" : "en"} onValueChange={(value) => void i18n.changeLanguage(value)}>
              <SelectTrigger id="language"><SelectValue /></SelectTrigger>
              <SelectContent>
                {SUPPORTED_LOCALES.map((locale) => <SelectItem key={locale} value={locale}>{locale === "ar" ? "العربية" : "English"}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
          <Field id="theme" label={t("settings.theme")}>
            <Select value={theme} onValueChange={(value) => setTheme(value as Theme)}>
              <SelectTrigger id="theme"><SelectValue /></SelectTrigger>
              <SelectContent>
                {THEMES.map((choice) => <SelectItem key={choice} value={choice}>{t(`settings.themes.${choice}`)}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.security")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Field id="lockAfter" label={t("settings.lockAfter")}>
            <Select value={String(lockAfter)} onValueChange={(value) => { setLockAfter(Number(value)); void writeMeta(META_KEYS.lockAfterSeconds, Number(value)) }}>
              <SelectTrigger id="lockAfter"><SelectValue /></SelectTrigger>
              <SelectContent>
                {LOCK_CHOICES.map((seconds) => <SelectItem key={seconds} value={String(seconds)}>{t("settings.seconds", { count: seconds })}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" onClick={lock} disabled={lockState !== "unlocked"}>{t("lock.title")}</Button>
            <Button variant="neutral" onClick={() => void removePin()}>{t("lock.remove")}</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.household")}</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={saveHousehold} className="flex flex-col gap-4">
            <Field id="householdName" label={t("settings.householdName")}>
              <Input id="householdName" value={householdName} onChange={(e) => setHouseholdName(e.target.value)} maxLength={80} required />
            </Field>
            <Field id="monthStartDay" label={t("settings.monthStartDay")}>
              <Input id="monthStartDay" type="number" min={1} max={28} dir="ltr" value={monthStartDay} onChange={(e) => setMonthStartDay(Number(e.target.value))} />
            </Field>
            <Button type="submit" className="self-start">{t("accounts.save")}</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.account")}</CardTitle></CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button variant="neutral" asChild><Link to="/sync">{t("sync.title")}</Link></Button>
          {canPromptInstall() && <Button variant="neutral" onClick={() => void promptInstall()}>{t("settings.install")}</Button>}
          <Button variant="neutral" onClick={() => void signOut()}>{t("auth.logout")}</Button>
        </CardContent>
      </Card>
    </div>
  )
}
