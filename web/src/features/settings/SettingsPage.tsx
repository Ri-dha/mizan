import { useEffect, useRef, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { toast } from "sonner"

import { cancelAccountDeletion, logout, requestAccountDeletion, updateHousehold, useSession } from "@/api/auth"
import { NOTIFICATION_CLASSES, setPreferences, usePreferences, type DigitStyle } from "@/app/preferences"
import { applyImport, buildExport, downloadJson, ImportError, validateImport, type ImportPreview } from "@/data/export"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
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
  const prefs = usePreferences()
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

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

  async function exportData() {
    if (!session) return
    downloadJson(`mizan-${session.householdName.replace(/\s+/g, "-")}-${new Date().toISOString().slice(0, 10)}.json`, await buildExport(session))
  }

  async function pickImport(file: File | undefined) {
    if (!file) return
    try {
      setPreview(validateImport(JSON.parse(await file.text())))
    } catch (e) {
      toast(e instanceof ImportError ? t("data.invalid", { reason: e.message }) : t("data.notJson"))
    } finally {
      if (fileInput.current) fileInput.current.value = ""
    }
  }

  async function confirmImport() {
    if (!preview) return
    const written = await applyImport(preview)
    setPreview(null)
    toast(t("data.imported", { count: written }))
  }

  async function deleteAccount() {
    try {
      await requestAccountDeletion()
      setConfirmDelete(false)
      toast(t("account.deletionRequested"))
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
        <CardHeader><CardTitle>{t("settings.display")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Field id="digits" label={t("settings.digits")} hint={t("settings.digitsHint")}>
            <Select value={prefs.digitStyle} onValueChange={(v) => setPreferences({ digitStyle: v as DigitStyle })}>
              <SelectTrigger id="digits"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="western">{t("settings.digitStyles.western")}</SelectItem>
                <SelectItem value="eastern">{t("settings.digitStyles.eastern")}</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <div className="flex items-center gap-2">
            <Switch id="showUsd" checked={prefs.showUsd} onCheckedChange={(on) => setPreferences({ showUsd: on })} />
            <Label htmlFor="showUsd">{t("settings.showUsd")}</Label>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.notifications")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("settings.notificationsBody")}</p>
          <div className="flex items-center gap-2">
            <Switch id="quiet" checked={prefs.quietMode} onCheckedChange={(on) => setPreferences({ quietMode: on })} />
            <Label htmlFor="quiet">{t("settings.quietMode")}</Label>
          </div>
          {NOTIFICATION_CLASSES.map((cls) => (
            <div key={cls} className="flex items-center gap-2">
              <Switch id={`n-${cls}`} disabled={prefs.quietMode} checked={prefs.notifications[cls] && !prefs.quietMode}
                onCheckedChange={(on) => setPreferences({ notifications: { ...prefs.notifications, [cls]: on } })} />
              <Label htmlFor={`n-${cls}`}>{t(`settings.notificationClasses.${cls}`)}</Label>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("data.title")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("data.body")}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" onClick={() => void exportData()}>{t("data.export")}</Button>
            <Button variant="neutral" onClick={() => fileInput.current?.click()}>{t("data.import")}</Button>
            <input ref={fileInput} type="file" accept="application/json,.json" hidden onChange={(e) => void pickImport(e.target.files?.[0])} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.account")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {session?.deletionRequestedAt && (
            <p className="rounded-base border-2 border-border p-3 text-sm">
              {t("account.deletionPending", { date: new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium" }).format(new Date(new Date(session.deletionRequestedAt).getTime() + 30 * 86_400_000)) })}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" asChild><Link to="/sync">{t("sync.title")}</Link></Button>
            {canPromptInstall() && <Button variant="neutral" onClick={() => void promptInstall()}>{t("settings.install")}</Button>}
            <Button variant="neutral" onClick={() => void signOut()}>{t("auth.logout")}</Button>
            {session?.deletionRequestedAt ? (
              <Button variant="neutral" onClick={() => void cancelAccountDeletion().then(() => toast(t("account.deletionCancelled")))}>{t("account.cancelDeletion")}</Button>
            ) : (
              <Button variant="neutral" onClick={() => setConfirmDelete(true)}>{t("account.delete")}</Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Dialog open={preview !== null} onOpenChange={(open) => !open && setPreview(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("data.previewTitle")}</DialogTitle>
            <DialogDescription>{t("data.previewBody")}</DialogDescription>
          </DialogHeader>
          <ul className="text-sm">
            {preview && Object.entries(preview.counts).map(([table, count]) => <li key={table}>{table}: {count}</li>)}
            {preview && preview.skipped.length > 0 && <li className="opacity-70">{t("data.skipped", { tables: preview.skipped.join(", ") })}</li>}
          </ul>
          <DialogFooter>
            <Button variant="neutral" onClick={() => setPreview(null)}>{t("common.cancel")}</Button>
            <Button onClick={() => void confirmImport()}>{t("data.confirmImport")}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("account.delete")}</DialogTitle>
            <DialogDescription>{t("account.deleteBody")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="neutral" onClick={() => void exportData()}>{t("data.export")}</Button>
            <Button onClick={() => void deleteAccount()}>{t("account.confirmDelete")}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
