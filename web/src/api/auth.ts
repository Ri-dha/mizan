import { useSyncExternalStore } from "react"

import { api, setAccessToken, setSessionLostHandler, unwrap } from "./client"
import { db } from "@/db/schema"
import { META_KEYS, deleteMeta, readMeta, writeMeta } from "@/db/meta"

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
}

type Listener = () => void

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

async function rememberFromServer(accessToken: string) {
  setAccessToken(accessToken)
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
  })
}

export async function register(identifier: string, password: string, displayName: string, locale: string) {
  const tokens = await unwrap(api.POST("/api/v1/auth/register", { body: { identifier, password, displayName, locale } }))
  await rememberFromServer(tokens.accessToken)
}

export async function login(identifier: string, password: string) {
  const tokens = await unwrap(api.POST("/api/v1/auth/login", { body: { identifier, password } }))
  await clearLocalData()
  await rememberFromServer(tokens.accessToken)
}

export async function refreshProfile() {
  const me = await unwrap(api.GET("/api/v1/me"))
  if (!session) return
  await persist({ ...session, verified: me.verified, householdName: me.household.name, monthStartDay: me.household.monthStartDay })
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
  await unwrap(api.POST("/api/v1/auth/password-reset/confirm", { body: { identifier, code, newPassword } }))
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

/** A different account must never see the previous one's rows; the whole local store goes. */
async function clearLocalData() {
  await db.transaction("rw", db.tables, async () => {
    for (const table of db.tables) await table.clear()
  })
}
