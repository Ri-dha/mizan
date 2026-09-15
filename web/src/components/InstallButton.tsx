import { Download } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { isIos, promptInstall, useInstallState } from "@/pwa/register"

interface Props {
  variant?: "default" | "neutral"
  size?: "default" | "sm"
  className?: string
}

/**
 * "Save as an app": the browser's own install prompt where one exists, otherwise the steps for
 * this browser. Hidden once Mizan already runs from the home screen.
 */
export function InstallButton({ variant = "default", size = "default", className }: Props) {
  const { t } = useTranslation()
  const state = useInstallState()
  const [showSteps, setShowSteps] = useState(false)
  if (state === "installed") return null

  async function install() {
    if (state === "promptable") {
      await promptInstall()
      return
    }
    setShowSteps(true)
  }

  const steps: string[] = isIos()
    ? [t("install.ios1"), t("install.ios2"), t("install.ios3")]
    : /Firefox/i.test(navigator.userAgent)
      ? [t("install.firefox1"), t("install.firefox2")]
      : [t("install.other1"), t("install.other2")]

  return (
    <>
      <Button variant={variant} size={size} className={className} onClick={() => void install()}><Download /> {t("install.button")}</Button>
      <Dialog open={showSteps} onOpenChange={setShowSteps}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("install.title")}</DialogTitle>
            <DialogDescription>{t("install.body")}</DialogDescription>
          </DialogHeader>
          <ol className="flex list-decimal flex-col gap-2 ps-5 text-sm">
            {steps.map((step) => <li key={step}>{step}</li>)}
          </ol>
        </DialogContent>
      </Dialog>
    </>
  )
}
