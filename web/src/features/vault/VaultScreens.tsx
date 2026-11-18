import { Copy, KeyRound } from "lucide-react"
import { useEffect, useState, type FormEvent, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { logout, unlockVault } from "@/api/auth"
import { describeError } from "@/app/errors"
import { Field } from "@/components/Field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { acknowledgeRecoveryCode, previewStartOver, recoverWithCode, regenerateRecoveryCode, startOver, useRecoveryCodeToShow, type ResetPreview } from "@/vault/account"

function CenteredCard({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><KeyRound /> {title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
    </div>
  )
}

/** A session from before encryption: the password opens (or creates) this account's keys. */
export function UnlockVaultScreen() {
  const { t } = useTranslation()
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await unlockVault(password)
    } catch (e) {
      setError(describeError(e))
    } finally {
      setBusy(false)
      setPassword("")
    }
  }

  return (
    <CenteredCard title={t("vault.unlockTitle")} description={t("vault.unlockBody")}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field id="vault-password" label={t("auth.password")}>
          <Input id="vault-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
        </Field>
        {error && <Alert><AlertDescription>{error}</AlertDescription></Alert>}
        <Button type="submit" disabled={busy}>{t("vault.unlock")}</Button>
        <Button type="button" variant="outline" onClick={() => void logout()}>{t("auth.logout")}</Button>
      </form>
    </CenteredCard>
  )
}

/** After a password reset the new password cannot open the old keys; the recovery code can. */
export function RecoveryScreen() {
  const { t } = useTranslation()
  const [code, setCode] = useState("")
  const [wrong, setWrong] = useState(false)
  const [busy, setBusy] = useState(false)
  const [startingOver, setStartingOver] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      setWrong(!(await recoverWithCode(code)))
    } catch (e) {
      toast(describeError(e))
    } finally {
      setBusy(false)
    }
  }

  if (startingOver) return <StartOverScreen onBack={() => setStartingOver(false)} />

  return (
    <CenteredCard title={t("vault.recoverTitle")} description={t("vault.recoverBody")}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field id="recovery-code" label={t("vault.recoveryCode")}>
          <Input id="recovery-code" autoComplete="off" spellCheck={false} className="font-mono uppercase" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus />
        </Field>
        {wrong && <Alert><AlertDescription>{t("vault.recoverFailed")}</AlertDescription></Alert>}
        <Button type="submit" disabled={busy}>{t("vault.recover")}</Button>
        <p className="text-sm text-muted-foreground">{t("vault.otherDevice")}</p>
        <Button type="button" variant="ghost" className="text-destructive" onClick={() => setStartingOver(true)}>{t("vault.noCode")}</Button>
        <Button type="button" variant="outline" onClick={() => void logout()}>{t("auth.logout")}</Button>
      </form>
    </CenteredCard>
  )
}

/** ADR e2e-3: says exactly what will be lost, from the server's own count, before anything is removed. */
function StartOverScreen({ onBack }: { onBack: () => void }) {
  const { t } = useTranslation()
  const [preview, setPreview] = useState<ResetPreview | null>(null)
  const [typed, setTyped] = useState("")
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    previewStartOver().then(setPreview).catch((e) => toast(describeError(e)))
  }, [])

  const word = t("vault.startOverWord")
  const sole = (preview?.households ?? []).filter((h) => h.soleKeyHolder)

  async function confirm(event: FormEvent) {
    event.preventDefault()
    if (typed.trim().toLocaleUpperCase() !== word.toLocaleUpperCase()) return
    setBusy(true)
    try {
      await startOver()
    } catch (e) {
      toast(describeError(e))
      setBusy(false)
    }
  }

  return (
    <CenteredCard title={t("vault.startOverTitle")} description={t("vault.startOverBody")}>
      {!preview ? <p className="text-sm text-muted-foreground">{t("common.loading")}</p> : (
        <form onSubmit={confirm} className="flex flex-col gap-4">
          <Alert variant="destructive">
            <AlertDescription className="flex flex-col gap-2">
              <span>{t("vault.startOverPrivate", { count: preview.privateRecords ?? 0 })}</span>
              {sole.map((h) => <span key={h.householdId}>{t("vault.startOverShared", { name: h.name, count: h.sharedRecordsLost ?? 0 })}</span>)}
              {sole.length === 0 && <span>{t("vault.startOverKept")}</span>}
            </AlertDescription>
          </Alert>
          <Field id="start-over-word" label={t("vault.startOverType", { word })}>
            <Input id="start-over-word" autoComplete="off" value={typed} onChange={(e) => setTyped(e.target.value)} required />
          </Field>
          <Button type="submit" variant="destructive" disabled={busy || typed.trim().toLocaleUpperCase() !== word.toLocaleUpperCase()}>{t("vault.startOverConfirm")}</Button>
          <Button type="button" variant="outline" onClick={onBack}>{t("vault.startOverBack")}</Button>
        </form>
      )}
    </CenteredCard>
  )
}

/** Shown once, right after the code is made; it is never stored anywhere the server can read. */
export function RecoveryCodeDialog() {
  const { t } = useTranslation()
  const code = useRecoveryCodeToShow()
  if (!code) return null

  async function copy() {
    try {
      await navigator.clipboard.writeText(code!)
      toast(t("vault.copied"))
    } catch {
      toast(t("common.error"))
    }
  }

  return (
    <Dialog open onOpenChange={() => undefined}>
      <DialogContent showCloseButton={false} onInteractOutside={(e) => e.preventDefault()} onEscapeKeyDown={(e) => e.preventDefault()}>
        <DialogHeader>
          <DialogTitle>{t("vault.recoveryTitle")}</DialogTitle>
          <DialogDescription>{t("vault.recoveryBody")}</DialogDescription>
        </DialogHeader>
        <p dir="ltr" className="rounded-lg border p-3 text-center font-mono text-lg tracking-wider break-all">{code}</p>
        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={() => void copy()}><Copy /> {t("vault.copy")}</Button>
          <Button onClick={() => void acknowledgeRecoveryCode()}>{t("vault.recoverySaved")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function RecoveryCodeCard() {
  const { t } = useTranslation()
  const [busy, setBusy] = useState(false)

  async function regenerate() {
    setBusy(true)
    try {
      await regenerateRecoveryCode()
    } catch (e) {
      toast(describeError(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("vault.settingsTitle")}</CardTitle>
        <CardDescription>{t("vault.settingsBody")}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" disabled={busy || !navigator.onLine} onClick={() => void regenerate()}>{t("vault.regenerate")}</Button>
      </CardContent>
    </Card>
  )
}
