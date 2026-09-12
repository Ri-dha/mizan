import { registerSW } from "virtual:pwa-register"
import { toast } from "sonner"

import i18n from "@/i18n"

export function registerPwa() {
  const updateServiceWorker = registerSW({
    onNeedRefresh() {
      toast(i18n.t("common.update"), {
        duration: Infinity,
        action: { label: i18n.t("common.reload"), onClick: () => void updateServiceWorker(true) },
      })
    },
  })

  if (navigator.storage?.persist) {
    void navigator.storage.persist()
  }
}

let deferredInstall: BeforeInstallPromptEvent | null = null

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
}

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault()
  deferredInstall = event as BeforeInstallPromptEvent
})

export function canPromptInstall() {
  return deferredInstall !== null
}

export async function promptInstall() {
  if (!deferredInstall) return
  await deferredInstall.prompt()
  deferredInstall = null
}
