import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router"
import { toast } from "sonner"

import { confirmPasswordReset, requestPasswordReset } from "@/api/auth"
import { describeError } from "@/app/errors"
import { AuthLayout } from "@/components/AuthLayout"
import { Field } from "@/components/Field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState("")
  const [code, setCode] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [requested, setRequested] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (!requested) {
        await requestPasswordReset(identifier)
        setRequested(true)
        toast(t("auth.resetRequested"))
      } else {
        await confirmPasswordReset(identifier, code, newPassword)
        toast(t("auth.resetDone"))
        void navigate("/login")
      }
    } catch (e) {
      setError(describeError(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={t("auth.resetTitle")}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field id="identifier" label={t("auth.identifier")}>
          <Input id="identifier" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required disabled={requested} />
        </Field>
        {requested && (
          <>
            <Field id="code" label={t("auth.resetCode")}>
              <Input id="code" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} required />
            </Field>
            <Field id="newPassword" label={t("auth.newPassword")}>
              <Input id="newPassword" type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required minLength={8} />
            </Field>
          </>
        )}
        {error && (
          <Alert>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={busy}>{requested ? t("auth.newPassword") : t("auth.resetTitle")}</Button>
        <Link to="/login" className="text-sm underline">{t("auth.login")}</Link>
      </form>
    </AuthLayout>
  )
}
