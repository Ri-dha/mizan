import { useCallback, useState, type FormEvent, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"

import { elevate } from "@/api/admin"
import { ApiError } from "@/api/client"
import { describeError } from "@/app/errors"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"

import { AdminActionContext, type Action, type Run } from "./adminAction"


/** ADR admin-1: a change refused for want of a fresh password asks for it, then runs again. */
export function AdminActionProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [pending, setPending] = useState<{ action: Action; done?: string; resolve: (ok: boolean) => void } | null>(null)
  const [password, setPassword] = useState("")
  const [busy, setBusy] = useState(false)

  const run = useCallback<Run>(async (action, done) => {
    try {
      await action()
      if (done) toast(done)
      return true
    } catch (e) {
      if (e instanceof ApiError && e.code === "REAUTHENTICATION_REQUIRED") {
        return new Promise<boolean>((resolve) => setPending({ action, done, resolve }))
      }
      toast(describeError(e))
      return false
    }
  }, [])

  async function confirm(event: FormEvent) {
    event.preventDefault()
    if (!pending) return
    setBusy(true)
    try {
      await elevate(password)
      await pending.action()
      if (pending.done) toast(pending.done)
      pending.resolve(true)
      setPending(null)
    } catch (e) {
      toast(describeError(e))
    } finally {
      setBusy(false)
      setPassword("")
    }
  }

  function cancel() {
    pending?.resolve(false)
    setPending(null)
    setPassword("")
  }

  return (
    <AdminActionContext.Provider value={run}>
      {children}
      <Dialog open={pending !== null} onOpenChange={(open) => !open && cancel()}>
        <DialogContent>
          <form onSubmit={confirm} className="flex flex-col gap-4">
            <DialogHeader>
              <DialogTitle>{t("admin.elevateTitle")}</DialogTitle>
              <DialogDescription>{t("admin.elevateBody")}</DialogDescription>
            </DialogHeader>
            <Field id="admin-password" label={t("auth.password")}>
              <Input id="admin-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
            </Field>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={cancel}>{t("common.cancel")}</Button>
              <Button type="submit" disabled={busy}>{t("admin.elevateConfirm")}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </AdminActionContext.Provider>
  )
}
