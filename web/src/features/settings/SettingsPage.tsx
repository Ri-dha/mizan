import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { toast } from "sonner"

import { cancelAccountDeletion, logout, requestAccountDeletion, useSession } from "@/api/auth"
import { setPreferences, usePreferences, type DigitStyle } from "@/app/preferences"
import { NOTIFICATION_SWITCHES, saveNotificationSettings, useNotificationSettings } from "@/db/notifications"
import { disablePush, enablePush, isIosBrowser, pushState, sendTestPush, type PushState } from "@/notifications/push"
import { Input } from "@/components/ui/input"
import { applyImport, buildExport, downloadJson, ImportError, validateImport, type ImportPreview } from "@/data/export"
import { assetsTable, downloadBlob, lotsTable, toCsv, toXlsx, transactionsTable, type Table } from "@/data/tabular"
import { db, type Attachment } from "@/db/schema"
import { CsvImportDialog } from "./CsvImportDialog"
import { clearBackupPassphrase, createBackup, openBackup, setAutomaticBackup, setBackupPassphrase, useBackupState, useBackups } from "@/backup/store"
import { todayIso } from "@/domain/calendar/month"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { describeError } from "@/app/errors"
import { setTheme, useTheme, type Theme } from "@/app/theme"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { META_KEYS, readMeta, writeMeta } from "@/db/meta"
import { SUPPORTED_LOCALES } from "@/i18n"
import { clearLock, currentDataKey, DEFAULT_LOCK_AFTER_SECONDS, removePin, lock, useLockState } from "@/lock/store"
import { enrollWebAuthn, hasWebAuthnUnlock, removeWebAuthn, webAuthnAvailable } from "@/lock/webauthn"
import { InstallButton } from "@/components/InstallButton"
import { resetSyncState } from "@/sync/engine"
import { HelpButton } from "@/components/HelpButton"
import { useScreenTour } from "@/tours/useTour"
import { forgetAllTours } from "@/tours/store"
import { PRIVACY_KINDS, savePrivacyDefault, usePrivacyDefaults } from "@/db/privacy"

const LOCK_CHOICES = [0, 30, 60, 300]
const THEMES: Theme[] = ["system", "light", "dark"]

export function SettingsPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const theme = useTheme()
  const lockState = useLockState()
  const [lockAfter, setLockAfter] = useState(DEFAULT_LOCK_AFTER_SECONDS)
  const [biometricsAvailable, setBiometricsAvailable] = useState(false)
  const [biometricsOn, setBiometricsOn] = useState(false)
  const privacy = usePrivacyDefaults()
  const notify = useNotificationSettings()
  const backup = useBackupState()
  const backups = useBackups()
  const [passphrase, setPassphrase] = useState("")
  const [passphraseDialog, setPassphraseDialog] = useState<"set" | { restore: Attachment } | null>(null)
  async function submitPassphrase() {
    try {
      if (passphraseDialog === "set") {
        await setBackupPassphrase(passphrase)
        await setAutomaticBackup(true)
        toast(t("backup.passphraseSet"))
      } else if (passphraseDialog) {
        setPreview(await openBackup(passphraseDialog.restore, passphrase))
      }
      setPassphraseDialog(null)
      setPassphrase("")
    } catch (e) {
      toast(e instanceof Error && e.message === "WRONG_PASSPHRASE" ? t("backup.wrongPassphrase") : describeError(e))
    }
  }
  async function backupNow() {
    if (!session) return
    try {
      await createBackup(session)
      toast(t("backup.queued"))
    } catch (e) {
      toast(describeError(e))
    }
  }
  const labels = (keys: string[]) => keys.map((k) => t(`exports.columns.${k}`))
  async function tableOf(kind: "transactions" | "metals" | "assets"): Promise<Table> {
    if (kind === "transactions") {
      const [rows, buckets] = await Promise.all([db.transactions.filter((x) => x.deletedAt === null).toArray(), db.buckets.toArray()])
      return transactionsTable(rows.sort((a, b) => (a.occurredOn < b.occurredOn ? -1 : 1)), buckets, labels(["date", "type", "amount", "currency", "baseAmount", "bucket", "category", "payee", "note"]))
    }
    if (kind === "metals") {
      const [lots, disposalLots] = await Promise.all([db.metalLots.filter((x) => x.deletedAt === null).toArray(), db.metalDisposalLots.filter((x) => x.deletedAt === null).toArray()])
      return lotsTable(lots, disposalLots, labels(["date", "metal", "purity", "form", "weightGrams", "remainingGrams", "metalCost", "makingCharge", "fees", "currency", "dealer", "note"]))
    }
    const [assets, valuations] = await Promise.all([db.assets.filter((x) => x.deletedAt === null).toArray(), db.assetValuations.filter((x) => x.deletedAt === null).toArray()])
    return assetsTable(assets, valuations, todayIso(), labels(["name", "type", "status", "purchaseDate", "purchasePrice", "currency", "value", "valuedOn", "liquidity", "soldOn", "salePrice"]))
  }
  async function exportTable(kind: "transactions" | "metals" | "assets", format: "csv" | "xlsx") {
    const table = await tableOf(kind)
    const stamp = new Date().toISOString().slice(0, 10)
    if (format === "csv") downloadBlob(`mizan-${kind}-${stamp}.csv`, new Blob([toCsv(table)], { type: "text/csv;charset=utf-8" }))
    else downloadBlob(`mizan-${kind}-${stamp}.xlsx`, new Blob([toXlsx(table, t(`exports.${kind}`), i18n.language === "ar")], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }))
  }
  const [push, setPush] = useState<PushState>("unsupported")
  useEffect(() => {
    void pushState().then(setPush)
  }, [])
  async function togglePush(on: boolean) {
    try {
      setPush(on ? await enablePush() : await disablePush())
    } catch (e) {
      toast(describeError(e))
    }
  }
  const prefs = usePreferences()
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const csvInput = useRef<HTMLInputElement>(null)
  const [csvFile, setCsvFile] = useState<File | null>(null)
  useScreenTour("settings")

  useEffect(() => {
    void readMeta<number>(META_KEYS.lockAfterSeconds).then((value) => value !== undefined && setLockAfter(value))
    void webAuthnAvailable().then(setBiometricsAvailable)
    void hasWebAuthnUnlock().then(setBiometricsOn)
  }, [])

  // Biometrics wrap the same data key the PIN unlocks, so enrolling needs the app unlocked, which Settings always is.
  async function toggleBiometrics(on: boolean) {
    if (!on) {
      await removeWebAuthn()
      setBiometricsOn(false)
      toast(t("lock.biometricsRemoved"))
      return
    }
    const key = currentDataKey()
    if (!key || !session) return
    try {
      const enrolled = await enrollWebAuthn(key, session.userId, session.displayName)
      setBiometricsOn(enrolled)
      toast(enrolled ? t("lock.biometricsEnabled") : t("lock.biometricsUnsupported"))
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
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-3xl">{t("settings.title")}</h1>
        <HelpButton tour="settings" />
      </div>

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

      <Card data-tour="settings-security">
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
          {biometricsAvailable && (
            <div className="flex items-center gap-2">
              <Switch id="biometrics" checked={biometricsOn} onCheckedChange={(on) => void toggleBiometrics(on)} disabled={lockState !== "unlocked"} />
              <Label htmlFor="biometrics">{t("lock.biometricsSwitch")}</Label>
            </div>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" onClick={lock} disabled={lockState !== "unlocked"}>{t("lock.title")}</Button>
            <Button variant="neutral" onClick={() => void removePin()}>{t("lock.remove")}</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t("settings.household")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("settings.householdBody", { name: session?.householdName ?? "", role: t(`household.roles.${session?.role ?? "OWNER"}`) })}</p>
          <Button variant="neutral" className="self-start" asChild><Link to="/household">{t("household.manage")}</Link></Button>
        </CardContent>
      </Card>

      <Card data-tour="settings-privacy">
        <CardHeader><CardTitle>{t("privacy.title")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("privacy.body")}</p>
          {PRIVACY_KINDS.map((kind) => (
            <div key={kind} className="flex items-center gap-2">
              <Switch id={`privacy-${kind}`} checked={privacy[kind] === "PRIVATE"} onCheckedChange={(on) => void savePrivacyDefault(kind, on ? "PRIVATE" : "SHARED")} />
              <Label htmlFor={`privacy-${kind}`}>{t(`privacy.kinds.${kind}`)}</Label>
            </div>
          ))}
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
            <Switch id="showHijri" checked={prefs.showHijri} onCheckedChange={(on) => setPreferences({ showHijri: on })} />
            <Label htmlFor="showHijri">{t("settings.showHijri")}</Label>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="showUsd" checked={prefs.showUsd} onCheckedChange={(on) => setPreferences({ showUsd: on })} />
            <Label htmlFor="showUsd">{t("settings.showUsd")}</Label>
          </div>
        </CardContent>
      </Card>

      <Card data-tour="settings-backup">
        <CardHeader><CardTitle>{t("backup.title")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("backup.body")}</p>
          {!backup.configured ? (
            <Button className="self-start" onClick={() => setPassphraseDialog("set")}>{t("backup.setPassphrase")}</Button>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <Switch id="backupAuto" checked={backup.automatic} onCheckedChange={(on) => void setAutomaticBackup(on)} />
                <Label htmlFor="backupAuto">{t("backup.automatic")}</Label>
              </div>
              <p className="text-xs opacity-70">{backup.lastBackupAt ? t("backup.last", { at: new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(backup.lastBackupAt)) }) : t("backup.never")}</p>
              <div className="flex flex-wrap gap-2">
                <Button variant="neutral" onClick={() => void backupNow()}>{t("backup.now")}</Button>
                <Button variant="neutral" onClick={() => setPassphraseDialog("set")}>{t("backup.changePassphrase")}</Button>
                <Button variant="neutral" onClick={() => void clearBackupPassphrase()}>{t("backup.forget")}</Button>
              </div>
            </>
          )}
          {backups.length > 0 && (
            <div className="flex flex-col gap-1 text-sm">
              <p className="font-heading">{t("backup.list")}</p>
              {backups.map((b) => (
                <div key={b.id} className="flex items-center justify-between gap-2">
                  <span>{b.uploadedAt ? new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(b.uploadedAt)) : t("backup.pendingUpload")} · {Math.round(b.byteSize / 1024)} KB</span>
                  <Button size="sm" variant="neutral" onClick={() => setPassphraseDialog({ restore: b })}>{t("backup.restore")}</Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={passphraseDialog !== null} onOpenChange={(open) => !open && setPassphraseDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{passphraseDialog === "set" ? t("backup.setPassphrase") : t("backup.restore")}</DialogTitle>
            <DialogDescription>{passphraseDialog === "set" ? t("backup.passphraseHint") : t("backup.restoreHint")}</DialogDescription>
          </DialogHeader>
          <form onSubmit={(e) => { e.preventDefault(); void submitPassphrase() }} className="flex flex-col gap-4">
            <Field id="passphrase" label={t("backup.passphrase")}>
              <Input id="passphrase" type="password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} required minLength={passphraseDialog === "set" ? 8 : 1} autoComplete="off" />
            </Field>
            <DialogFooter><Button type="submit">{passphraseDialog === "set" ? t("accounts.save") : t("backup.open")}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Card data-tour="settings-notifications">
        <CardHeader><CardTitle>{t("settings.notifications")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <p className="text-sm opacity-70">{t("settings.notificationsBody")}</p>
          <div className="flex flex-wrap items-center gap-2">
            {push === "unsupported" && <p className="text-sm">{t("notifications.unsupported")}</p>}
            {push === "denied" && <p className="text-sm">{t("notifications.denied")}</p>}
            {(push === "off" || push === "on") && (
              <>
                <Switch id="push" checked={push === "on"} onCheckedChange={(on) => void togglePush(on)} />
                <Label htmlFor="push">{t("notifications.onThisDevice")}</Label>
              </>
            )}
            {push === "on" && <Button size="sm" variant="neutral" onClick={() => void sendTestPush().then((n) => toast(t("notifications.testSent", { count: n }))).catch((e) => toast(describeError(e)))}>{t("notifications.sendTest")}</Button>}
          </div>
          {isIosBrowser() && <p className="text-xs opacity-70">{t("notifications.iosHint")}</p>}
          <div className="flex items-center gap-2">
            <Switch id="quiet" checked={notify.quietMode} onCheckedChange={(on) => void saveNotificationSettings({ quietMode: on })} />
            <Label htmlFor="quiet">{t("settings.quietMode")}</Label>
          </div>
          {NOTIFICATION_SWITCHES.map((cls) => (
            <div key={cls} className="flex flex-wrap items-center gap-2">
              <Switch id={`n-${cls}`} disabled={notify.quietMode} checked={notify[cls] && !notify.quietMode}
                onCheckedChange={(on) => void saveNotificationSettings({ [cls]: on })} />
              <Label htmlFor={`n-${cls}`}>{t(`settings.notificationClasses.${cls}`)}</Label>
              {cls === "billDue" && <Input aria-label={t("notifications.leadDays")} type="number" min={0} max={30} dir="ltr" className="w-20" value={notify.billLeadDays} onChange={(e) => void saveNotificationSettings({ billLeadDays: Math.max(0, Math.min(30, Number(e.target.value) || 0)) })} />}
              {cls === "overspend" && <Input aria-label={t("notifications.thresholdPercent")} type="number" min={1} max={200} dir="ltr" className="w-20" value={notify.overspendThresholdBp / 100} onChange={(e) => void saveNotificationSettings({ overspendThresholdBp: Math.max(100, Math.min(20000, Math.round(Number(e.target.value) * 100) || 9000)) })} />}
              {cls === "metalPrice" && <Input aria-label={t("notifications.movePercent")} type="number" min={0.1} max={50} step={0.1} dir="ltr" className="w-20" value={notify.metalMoveBp / 100} onChange={(e) => void saveNotificationSettings({ metalMoveBp: Math.max(10, Math.min(5000, Math.round(Number(e.target.value) * 100) || 200)) })} />}
            </div>
          ))}
        </CardContent>
      </Card>

      <Card data-tour="settings-data">
        <CardHeader><CardTitle>{t("data.title")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm opacity-70">{t("data.body")}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" onClick={() => void exportData()}>{t("data.export")}</Button>
            <Button variant="neutral" onClick={() => fileInput.current?.click()}>{t("data.import")}</Button>
            <input ref={fileInput} type="file" accept="application/json,.json" hidden onChange={(e) => void pickImport(e.target.files?.[0])} />
            <Button variant="neutral" onClick={() => csvInput.current?.click()}>{t("csv.button")}</Button>
            <input ref={csvInput} type="file" accept="text/csv,.csv" hidden onChange={(e) => { setCsvFile(e.target.files?.[0] ?? null); e.target.value = "" }} />
          </div>
                  <div className="flex flex-col gap-2">
            <p className="text-sm opacity-70">{t("exports.body")}</p>
            {(["transactions", "metals", "assets"] as const).map((kind) => (
              <div key={kind} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="w-32">{t(`exports.${kind}`)}</span>
                <Button size="sm" variant="neutral" onClick={() => void exportTable(kind, "csv")}>CSV</Button>
                <Button size="sm" variant="neutral" onClick={() => void exportTable(kind, "xlsx")}>Excel</Button>
              </div>
            ))}
          </div>
</CardContent>
      </Card>

      <Card data-tour="settings-account">
        <CardHeader><CardTitle>{t("settings.account")}</CardTitle></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {session?.deletionRequestedAt && (
            <p className="rounded-base border-2 border-border p-3 text-sm">
              {t("account.deletionPending", { date: new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium" }).format(new Date(new Date(session.deletionRequestedAt).getTime() + 30 * 86_400_000)) })}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="neutral" asChild><Link to="/sync">{t("sync.title")}</Link></Button>
            <Button variant="neutral" asChild><Link to="/help">{t("help.title")}</Link></Button>
            <Button variant="neutral" onClick={() => { forgetAllTours(); toast(t("tours.replayed")) }}>{t("tours.replay")}</Button>
            <InstallButton variant="neutral" />
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
      <CsvImportDialog file={csvFile} onClose={() => setCsvFile(null)} />
    </div>
  )
}
