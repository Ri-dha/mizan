import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { toast } from "sonner"

import { resendVerification, useSession, verifyContact } from "@/api/auth"
import { describeError } from "@/app/errors"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"

const CODE_LENGTH = 6

export function VerifyPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const session = useSession()
  const [code, setCode] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function confirm(value: string) {
    setBusy(true)
    setError(null)
    try {
      await verifyContact(value)
      toast(t("auth.verified"))
      void navigate("/")
    } catch (e) {
      setError(describeError(e))
      setCode("")
    } finally {
      setBusy(false)
    }
  }

  async function resend() {
    try {
      await resendVerification()
      toast(t("auth.codeSent"))
    } catch (e) {
      setError(describeError(e))
    }
  }

  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>{t("auth.verifyTitle")}</CardTitle>
        <CardDescription>{t("auth.verifyBody", { contact: session?.contact })}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-4" dir="ltr">
        <InputOTP maxLength={CODE_LENGTH} value={code} onChange={setCode} onComplete={confirm} disabled={busy}>
          <InputOTPGroup>
            {Array.from({ length: CODE_LENGTH }, (_, i) => <InputOTPSlot key={i} index={i} />)}
          </InputOTPGroup>
        </InputOTP>
        {error && (
          <Alert>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <div className="flex gap-2">
          <Button variant="neutral" onClick={resend}>{t("auth.resend")}</Button>
          <Button variant="neutral" onClick={() => void navigate("/")}>{t("auth.skipForNow")}</Button>
        </div>
      </CardContent>
    </Card>
  )
}
