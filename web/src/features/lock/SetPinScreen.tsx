import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { setPin } from "@/lock/store"
import { enrollWebAuthn, webAuthnAvailable } from "@/lock/webauthn"

const PIN_PATTERN = /^\d{4,8}$/

export function SetPinScreen() {
  const { t } = useTranslation()
  const session = useSession()
  const [pin, setPinValue] = useState("")
  const [confirm, setConfirm] = useState("")
  const [biometricOffered, setBiometricOffered] = useState(false)
  const [enrollBiometric, setEnrollBiometric] = useState(false)
  const [mismatch, setMismatch] = useState(false)

  useEffect(() => {
    void webAuthnAvailable().then(setBiometricOffered)
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pin !== confirm) {
      setMismatch(true)
      return
    }
    const key = await setPin(pin)
    if (enrollBiometric && session) {
      try {
        await enrollWebAuthn(key, session.userId, session.displayName)
      } catch {
        // The PIN is already saved; a refused biometric prompt just means PIN only.
      }
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>{t("lock.setTitle")}</CardTitle>
          <CardDescription>{t("lock.setBody")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-col gap-3">
            <Label htmlFor="pin">{t("lock.pin")}</Label>
            <Input id="pin" type="password" inputMode="numeric" autoComplete="new-password" value={pin}
              onChange={(e) => { setPinValue(e.target.value.replace(/\D/g, "")); setMismatch(false) }} maxLength={8} />
            <Label htmlFor="confirm">{t("lock.confirmPin")}</Label>
            <Input id="confirm" type="password" inputMode="numeric" autoComplete="new-password" value={confirm}
              onChange={(e) => { setConfirm(e.target.value.replace(/\D/g, "")); setMismatch(false) }} maxLength={8} />
            {mismatch && <p className="text-sm">{t("lock.pinMismatch")}</p>}
            {biometricOffered && (
              <div className="flex items-center gap-2">
                <Switch id="biometric" checked={enrollBiometric} onCheckedChange={setEnrollBiometric} />
                <Label htmlFor="biometric">{t("lock.enrollBiometric")}</Label>
              </div>
            )}
            <Button type="submit" disabled={!PIN_PATTERN.test(pin)}>{t("lock.save")}</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
