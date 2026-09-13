import { db } from "./schema"

export const META_KEYS = {
  session: "session",
  deviceId: "sync.deviceId",
  pullCursor: "sync.pullCursor",
  lastSyncAt: "sync.lastSyncAt",
  hlc: "sync.hlc",
  pin: "lock.pin",
  webauthn: "lock.webauthn",
  lockAfterSeconds: "lock.afterSeconds",
  backupKey: "backup.key",
  backupAuto: "backup.auto",
  backupLastAt: "backup.lastAt",
} as const

export async function readMeta<T>(key: string): Promise<T | undefined> {
  const entry = await db.meta.get(key)
  return entry?.value as T | undefined
}

export async function writeMeta(key: string, value: unknown) {
  await db.meta.put({ key, value })
}

export async function deleteMeta(key: string) {
  await db.meta.delete(key)
}
