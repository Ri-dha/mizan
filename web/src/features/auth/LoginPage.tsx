import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { login } from "@/api/auth"
import { describeError } from "@/app/errors"
import { AuthLayout } from "@/components/AuthLayout"
import { Field } from "@/components/Field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export function LoginPage() {
  const { t } = useTranslation()
  const [identifier, setIdentifier] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(identifier, password)
    } catch (e) {
      setError(describeError(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={t("auth.login")}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field id="identifier" label={t("auth.identifier")} hint={t("auth.identifierHint")}>
          <Input id="identifier" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
        </Field>
        <Field id="password" label={t("auth.password")}>
          <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        {error && (
          <Alert>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {!navigator.onLine && (
          <Alert>
            <AlertDescription>{t("auth.offlineNeedsSession")}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={busy}>{t("auth.login")}</Button>
        <div className="flex justify-between text-sm">
          <Link to="/register" className="underline">{t("auth.noAccount")} {t("auth.register")}</Link>
          <Link to="/reset" className="underline">{t("auth.forgot")}</Link>
        </div>
      </form>
    </AuthLayout>
  )
}
