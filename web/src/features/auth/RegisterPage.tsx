import { useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { register } from "@/api/auth"
import { describeError } from "@/app/errors"
import { AuthLayout } from "@/components/AuthLayout"
import { Field } from "@/components/Field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export function RegisterPage() {
  const { t, i18n } = useTranslation()
  const [identifier, setIdentifier] = useState("")
  const [displayName, setDisplayName] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(identifier, password, displayName, i18n.language.startsWith("ar") ? "ar" : "en")
    } catch (e) {
      setError(describeError(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={t("auth.register")}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field id="displayName" label={t("auth.displayName")}>
          <Input id="displayName" autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={80} />
        </Field>
        <Field id="identifier" label={t("auth.identifier")} hint={t("auth.identifierHint")}>
          <Input id="identifier" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
        </Field>
        <Field id="password" label={t("auth.password")}>
          <Input id="password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
        </Field>
        {error && (
          <Alert>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={busy}>{t("auth.register")}</Button>
        <Link to="/login" className="text-sm underline">{t("auth.haveAccount")} {t("auth.login")}</Link>
      </form>
    </AuthLayout>
  )
}
