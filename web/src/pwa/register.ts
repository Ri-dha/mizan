import { useSyncExternalStore } from "react"

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
const installListeners = new Set<() => void>()

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice?: Promise<{ outcome: "accepted" | "dismissed" }>
}

const emitInstallChange = () => installListeners.forEach((l) => l())

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault()
  deferredInstall = event as BeforeInstallPromptEvent
  emitInstallChange()
})
window.addEventListener("appinstalled", () => {
  deferredInstall = null
  emitInstallChange()
})

export type InstallState = "installed" | "promptable" | "manual"

/** Running from the home screen (or as a desktop app) rather than in a browser tab. */
export function isStandalone(): boolean {
  const standaloneDisplay = typeof window.matchMedia === "function" && window.matchMedia("(display-mode: standalone)").matches
  return standaloneDisplay || (navigator as Navigator & { standalone?: boolean }).standalone === true
}

export function isIos(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
}

/** Installed: nothing to do. Chrome and Edge: a real prompt. Safari and Firefox: instructions. */
export function installState(): InstallState {
  if (isStandalone()) return "installed"
  return deferredInstall ? "promptable" : "manual"
}

export function useInstallState(): InstallState {
  return useSyncExternalStore(
    (listener) => {
      installListeners.add(listener)
      const media = typeof window.matchMedia === "function" ? window.matchMedia("(display-mode: standalone)") : null
      media?.addEventListener("change", listener)
      return () => {
        installListeners.delete(listener)
        media?.removeEventListener("change", listener)
      }
    },
    installState,
    () => "manual" as InstallState,
  )
}

export function canPromptInstall() {
  return deferredInstall !== null
}

export async function promptInstall(): Promise<boolean> {
  if (!deferredInstall) return false
  const event = deferredInstall
  await event.prompt()
  const choice = await event.userChoice?.catch(() => undefined)
  deferredInstall = null
  emitInstallChange()
  return choice?.outcome !== "dismissed"
}
