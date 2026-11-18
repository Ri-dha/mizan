import { useSyncExternalStore } from "react"

import { api, ApiError, setAccessToken, setSessionLostHandler, unwrap } from "./client"
import { db } from "@/db/schema"
import { META_KEYS, deleteMeta, readMeta, writeMeta } from "@/db/meta"
import { derivePasswordKeys } from "@/vault/crypto"
import { forgetVault, newAccountKeys, openAccountAfterSignIn, type VaultState } from "@/vault/account"
import { clearKeyring, DEVICE_META_KEYS, dropSharedKeys } from "@/vault/keyring"
import { resetLock } from "@/lock/store"

export interface LocalSession {
  userId: string
  householdId: string
  householdName: string
  displayName: string
  contact: string
  locale: string
  verified: boolean
  role: string
  baseCurrency: string
  monthStartDay: number
  deletionRequestedAt: string | null
  householdDeletionRequestedAt: string | null
  /** Runs the service (ADR admin-1); the server still checks on every admin call. */
  operator?: boolean
}

type Listener = () => void

const UNAUTHORIZED = 401

let session: LocalSession | null | undefined
const listeners = new Set<Listener>()

function emit() {
  listeners.forEach((listener) => listener())
}

async function persist(next: LocalSession | null) {
  session = next
  if (next) await writeMeta(META_KEYS.session, next)
  else await deleteMeta(META_KEYS.session)
  emit()
}

/** Loads the remembered session so the app opens offline; the token itself is fetched lazily. */
export async function loadSession(): Promise<LocalSession | null> {
  if (session === undefined) {
    session = (await readMeta<LocalSession>(META_KEYS.session)) ?? null
    emit()
  }
  return session
}

export function useSession(): LocalSession | null | undefined {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => session,
  )
}

setSessionLostHandler(() => {
  void persist(null)
})

/** A null token keeps the one already obtained through a refresh. */
export async function rememberFromServer(accessToken: string | null) {
  if (accessToken) setAccessToken(accessToken)
  const me = await unwrap(api.GET("/api/v1/me"))
  await persist({
    userId: me.id,
    householdId: me.household.id,
    householdName: me.household.name,
    displayName: me.displayName,
    contact: me.email ?? me.phone ?? "",
    locale: me.locale,
    verified: me.verified,
    role: me.household.role,
    baseCurrency: me.household.baseCurrency,
    monthStartDay: me.household.monthStartDay,
    deletionRequestedAt: me.deletionRequestedAt ?? null,
    householdDeletionRequestedAt: me.household.deletionRequestedAt ?? null,
    operator: me.operator ?? false,
  })
}

/** ADR e2e-1: the password never leaves the device; the server gets an auth key derived from it. */
export async function register(identifier: string, password: string, displayName: string, locale: string) {
  const { authKey, wrapSecret } = await derivePasswordKeys(identifier, password)
  const keys = await newAccountKeys(wrapSecret)
  const tokens = await unwrap(api.POST("/api/v1/auth/register", { body: { identifier, authKey, displayName, locale, keys: keys.material } }))
  await clearLocalData()
  await rememberFromServer(tokens.accessToken)
  await keys.finish()
}

/**
 * An account from before ADR e2e-1 still has a hash of the password itself, so a refused
 * sign-in is retried once with it; the server then replaces that hash with the auth key's.
 */
export async function login(identifier: string, password: string): Promise<VaultState> {
  const { authKey, wrapSecret } = await derivePasswordKeys(identifier, password)
  const tokens = await unwrap(api.POST("/api/v1/auth/login", { body: { identifier, authKey } })).catch(async (error: unknown) => {
    if (!(error instanceof ApiError) || error.status !== UNAUTHORIZED) throw error
    return unwrap(api.POST("/api/v1/auth/login", { body: { identifier, authKey, legacyPassword: password } }))
  })
  await clearLocalData()
  await rememberFromServer(tokens.accessToken)
  return openAccountAfterSignIn(wrapSecret)
}

/**
 * A session from before ADR e2e-1 has no keys on this device. Signing in again proves the
 * password and derives them, without clearing rows that may still be waiting to sync.
 */
export async function unlockVault(password: string): Promise<VaultState> {
  const identifier = session?.contact ?? ""
  const { authKey, wrapSecret } = await derivePasswordKeys(identifier, password)
  const tokens = await unwrap(api.POST("/api/v1/auth/login", { body: { identifier, authKey } })).catch(async (error: unknown) => {
    if (!(error instanceof ApiError) || error.status !== UNAUTHORIZED) throw error
    return unwrap(api.POST("/api/v1/auth/login", { body: { identifier, authKey, legacyPassword: password } }))
  })
  setAccessToken(tokens.accessToken)
  return openAccountAfterSignIn(wrapSecret)
}

/** Called on every sync, so a role granted or changed elsewhere shows without signing in again. */
export async function refreshProfile() {
  const me = await unwrap(api.GET("/api/v1/me"))
  if (!session) return
  const next = { ...session, verified: me.verified, householdName: me.household.name, monthStartDay: me.household.monthStartDay, role: me.household.role, deletionRequestedAt: me.deletionRequestedAt ?? null, householdDeletionRequestedAt: me.household.deletionRequestedAt ?? null, operator: me.operator ?? false }
  if (JSON.stringify(next) !== JSON.stringify(session)) await persist(next)
}

/** FR-ACC-08: the account and household are purged after the grace period unless withdrawn. */
export async function requestAccountDeletion() {
  const me = await unwrap(api.POST("/api/v1/me/deletion-request"))
  if (session) await persist({ ...session, deletionRequestedAt: me.deletionRequestedAt ?? null })
}

export async function cancelAccountDeletion() {
  const me = await unwrap(api.DELETE("/api/v1/me/deletion-request"))
  if (session) await persist({ ...session, deletionRequestedAt: me.deletionRequestedAt ?? null })
}

export async function verifyContact(code: string) {
  await unwrap(api.POST("/api/v1/auth/verify", { body: { code } }))
  await refreshProfile()
}

export async function resendVerification() {
  await unwrap(api.POST("/api/v1/auth/verify/resend"))
}

export async function requestPasswordReset(identifier: string) {
  await unwrap(api.POST("/api/v1/auth/password-reset/request", { body: { identifier } }))
}

export async function confirmPasswordReset(identifier: string, code: string, newPassword: string) {
  const { authKey } = await derivePasswordKeys(identifier, newPassword)
  await unwrap(api.POST("/api/v1/auth/password-reset/confirm", { body: { identifier, code, newAuthKey: authKey } }))
}

export async function authKeyFor(password: string): Promise<string> {
  return (await derivePasswordKeys(session?.contact ?? "", password)).authKey
}

export async function updateHousehold(changes: { name?: string; monthStartDay?: number }) {
  const household = await unwrap(api.PATCH("/api/v1/households/current", { body: changes }))
  if (!session) return
  await persist({ ...session, householdName: household.name, monthStartDay: household.monthStartDay })
}

export async function logout() {
  try {
    await api.POST("/api/v1/auth/logout")
  } catch {
    // Offline sign-out still clears the device; the refresh token expires on its own.
  }
  setAccessToken(null)
  await clearLocalData()
  await persist(null)
}

const DEVICE_META: readonly string[] = [META_KEYS.pin, META_KEYS.webauthn, META_KEYS.lockAfterSeconds, ...DEVICE_META_KEYS]

/**
 * A change of household keeps what belongs to the person and the device (the PIN, the
 * account's own keys) and drops every row and shared key of the household being left.
 */
export async function clearHouseholdData() {
  const kept = (await db.meta.bulkGet([...DEVICE_META])).filter((entry) => entry !== undefined)
  await db.transaction("rw", db.tables, async () => {
    for (const table of db.tables) await table.clear()
    await db.meta.bulkPut(kept)
  })
  await dropSharedKeys()
}

/** A different account must never see the previous one's rows; the whole local store goes. */
export async function clearLocalData() {
  await clearKeyring()
  await db.transaction("rw", db.tables, async () => {
    for (const table of db.tables) await table.clear()
  })
  forgetVault()
  await resetLock()
}
