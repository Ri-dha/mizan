import { useLiveQuery } from "dexie-react-hooks"

import { db, type PrivacySetting, type Visibility } from "./schema"
import { writeFields } from "./write"

export type PrivacyKind = "transactions" | "accounts" | "metals" | "debts" | "goals" | "assets"

export const PRIVACY_KINDS: PrivacyKind[] = ["transactions", "accounts", "metals", "debts", "goals", "assets"]

const SHARED_DEFAULTS: Record<PrivacyKind, Visibility> = {
  transactions: "SHARED", accounts: "SHARED", metals: "SHARED", debts: "SHARED", goals: "SHARED", assets: "SHARED",
}

/** BR-16: the member's own row; pulled rows from other members never reach this device. */
export const livePrivacySetting = () => db.privacySettings.filter((p) => p.deletedAt === null).first()

export function defaultsOf(setting: PrivacySetting | undefined): Record<PrivacyKind, Visibility> {
  if (!setting) return SHARED_DEFAULTS
  return { transactions: setting.transactions, accounts: setting.accounts, metals: setting.metals, debts: setting.debts, goals: setting.goals, assets: setting.assets }
}

export function usePrivacyDefaults(): Record<PrivacyKind, Visibility> {
  const setting = useLiveQuery(livePrivacySetting, [], undefined)
  return defaultsOf(setting)
}

export async function savePrivacyDefault(kind: PrivacyKind, visibility: Visibility) {
  const existing = await livePrivacySetting()
  const id = existing?.id ?? crypto.randomUUID()
  await writeFields<PrivacySetting>("privacy_setting", id, { ...(existing ? {} : { ...SHARED_DEFAULTS, visibility: "PRIVATE" as const }), [kind]: visibility })
}
