import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router"
import { toast } from "sonner"

import { useSession } from "@/api/auth"
import { acceptInvitation, previewInvitation, rememberPendingInvite } from "@/api/household"
import { describeError } from "@/app/errors"
import { AuthLayout } from "@/components/AuthLayout"
import { Button } from "@/components/ui/button"

type Preview = Awaited<ReturnType<typeof previewInvitation>>

/** The landing page of an invitation link; works before sign-in and finishes the join after it. */
export function JoinPage() {
  const { t, i18n } = useTranslation()
  const { token = "" } = useParams()
  const session = useSession()
  const navigate = useNavigate()
  const [preview, setPreview] = useState<Preview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    previewInvitation(token).then(setPreview).catch((e) => setError(describeError(e)))
  }, [token])

  async function join() {
    setBusy(true)
    try {
      await acceptInvitation(token)
      toast(t("household.joined"))
      navigate("/", { replace: true })
    } catch (e) {
      toast(describeError(e))
      setBusy(false)
    }
  }

  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium" }).format(new Date(iso))

  return (
    <AuthLayout title={t("household.joinTitle")}>
      <div className="flex flex-col gap-4">
        {error && <p className="text-chart-2">{error}</p>}
        {preview && (
          <>
            <p>{t("household.joinBody", { household: preview.householdName, by: preview.invitedBy, role: t(`household.roles.${preview.role}`) })}</p>
            <p className="text-sm opacity-70">{t("household.expires", { date: date(preview.expiresAt) })}</p>
            {session ? (
              <Button onClick={() => void join()} disabled={busy}>{t("household.join")}</Button>
            ) : (
              <div className="flex flex-col gap-2">
                <p className="text-sm">{t("household.joinSignIn")}</p>
                <Button asChild onClick={() => rememberPendingInvite(token)}><Link to="/login">{t("auth.login")}</Link></Button>
                <Button asChild variant="neutral" onClick={() => rememberPendingInvite(token)}><Link to="/register">{t("auth.register")}</Link></Button>
              </div>
            )}
          </>
        )}
        <Link to="/" className="text-sm underline">{t("household.backHome")}</Link>
      </div>
    </AuthLayout>
  )
}
