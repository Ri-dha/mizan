import { useSyncExternalStore } from "react"

import { META_KEYS, deleteMeta, readMeta, writeMeta } from "@/db/meta"
import { generateDataKey, secretFromPin, unwrapDataKey, wrapDataKey, type WrappedKey } from "./crypto"
import { removeWebAuthn, unlockWithWebAuthn } from "./webauthn"

export const DEFAULT_LOCK_AFTER_SECONDS = 30

export type LockState = "unknown" | "no-pin" | "locked" | "unlocked"

let state: LockState = "unknown"
let dataKey: CryptoKey | null = null
let hiddenAt: number | null = null
const listeners = new Set<() => void>()

function set(next: LockState) {
  state = next
  listeners.forEach((listener) => listener())
}

export function useLockState(): LockState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => state,
  )
}

export function currentDataKey(): CryptoKey | null {
  return dataKey
}

export async function initialiseLock() {
  const pin = await readMeta<WrappedKey>(META_KEYS.pin)
  set(pin ? "locked" : "no-pin")
}

export async function setPin(pin: string): Promise<CryptoKey> {
  const key = dataKey ?? (await generateDataKey())
  await writeMeta(META_KEYS.pin, await wrapDataKey(key, secretFromPin(pin)))
  dataKey = key
  set("unlocked")
  return key
}

export async function unlockWithPin(pin: string): Promise<boolean> {
  const entry = await readMeta<WrappedKey>(META_KEYS.pin)
  if (!entry) return false
  const key = await unwrapDataKey(entry, secretFromPin(pin))
  if (!key) return false
  dataKey = key
  set("unlocked")
  return true
}

export async function unlockWithBiometrics(): Promise<boolean> {
  const key = await unlockWithWebAuthn()
  if (!key) return false
  dataKey = key
  set("unlocked")
  return true
}

export async function removePin() {
  await deleteMeta(META_KEYS.pin)
  await removeWebAuthn()
  dataKey = null
  set("no-pin")
}

export function lock() {
  if (state === "unlocked") {
    dataKey = null
    set("locked")
  }
}

export async function clearLock() {
  dataKey = null
  await deleteMeta(META_KEYS.pin)
  await removeWebAuthn()
  set("unknown")
}

/** FR-ACC-03: leaving the app for longer than the configured grace re-locks it. */
export function startLockWatcher() {
  document.addEventListener("visibilitychange", async () => {
    if (document.visibilityState === "hidden") {
      hiddenAt = Date.now()
      document.body.classList.add("privacy-blur")
      return
    }
    document.body.classList.remove("privacy-blur")
    if (hiddenAt === null) return
    const after = (await readMeta<number>(META_KEYS.lockAfterSeconds)) ?? DEFAULT_LOCK_AFTER_SECONDS
    if (Date.now() - hiddenAt >= after * 1000) lock()
    hiddenAt = null
  })
}
