import { Fingerprint } from "lucide-react"
import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { unlockWithBiometrics, unlockWithPin } from "@/lock/store"
import { hasWebAuthnUnlock } from "@/lock/webauthn"

export function LockScreen() {
  const { t } = useTranslation()
  const [pin, setPin] = useState("")
  const [wrong, setWrong] = useState(false)
  const [biometric, setBiometric] = useState(false)

  useEffect(() => {
    void hasWebAuthnUnlock().then(setBiometric)
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const ok = await unlockWithPin(pin)
    setWrong(!ok)
    setPin("")
  }

  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>{t("lock.title")}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-col gap-3">
            <Input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              aria-label={t("lock.enterPin")}
              placeholder={t("lock.enterPin")}
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
              maxLength={8}
            />
            {wrong && <p className="text-sm">{t("lock.wrongPin")}</p>}
            <Button type="submit" disabled={pin.length < 4}>{t("lock.unlock")}</Button>
            {biometric && (
              <Button type="button" variant="neutral" onClick={() => void unlockWithBiometrics().then((ok) => setWrong(!ok))}>
                <Fingerprint /> {t("lock.biometric")}
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
