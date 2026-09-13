import { gunzipSync, gzipSync, strFromU8, strToU8 } from "fflate"
import { useLiveQuery } from "dexie-react-hooks"

import { api, unwrap } from "@/api/client"
import type { LocalSession } from "@/api/auth"
import { buildExport, validateImport, type ImportPreview } from "@/data/export"
import { META_KEYS, readMeta, writeMeta, deleteMeta } from "@/db/meta"
import { db, type Attachment } from "@/db/schema"
import { softDelete, writeFields } from "@/db/write"

export const BACKUP_OWNER_TYPE = "BACKUP"
const MAGIC = strToU8("MZB1")
const SALT_BYTES = 16
const IV_BYTES = 12
const ITERATIONS = 310_000
const KEEP_LAST = 7
const DAY_MS = 24 * 60 * 60 * 1000
const MIME = "application/octet-stream"

interface StoredBackupKey {
  salt: number[]
  iterations: number
  key: JsonWebKey
}

export interface BackupState {
  configured: boolean
  automatic: boolean
  lastBackupAt: number | null
}

const toBase64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes))
const fromBase64 = (text: string) => Uint8Array.from(atob(text), (c) => c.charCodeAt(0))

/** FR-DAT-05: the passphrase is separate from the PIN because the PIN's key never leaves this device. */
async function deriveKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey("raw", strToU8(passphrase.normalize("NFKC")), "PBKDF2", false, ["deriveKey"])
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", salt: salt as BufferSource, iterations, hash: "SHA-256" },
    material, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"],
  )
}

export async function setBackupPassphrase(passphrase: string) {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES))
  const key = await deriveKey(passphrase, salt, ITERATIONS)
  const stored: StoredBackupKey = { salt: [...salt], iterations: ITERATIONS, key: await crypto.subtle.exportKey("jwk", key) }
  await writeMeta(META_KEYS.backupKey, stored)
}

export async function clearBackupPassphrase() {
  await deleteMeta(META_KEYS.backupKey)
  await writeMeta(META_KEYS.backupAuto, false)
}

export async function setAutomaticBackup(on: boolean) {
  await writeMeta(META_KEYS.backupAuto, on)
}

export async function backupState(): Promise<BackupState> {
  const [key, auto, last] = await Promise.all([readMeta<StoredBackupKey>(META_KEYS.backupKey), readMeta<boolean>(META_KEYS.backupAuto), readMeta<number>(META_KEYS.backupLastAt)])
  return { configured: !!key, automatic: auto ?? false, lastBackupAt: last ?? null }
}

export function useBackupState(): BackupState {
  return useLiveQuery(backupState, [], { configured: false, automatic: false, lastBackupAt: null })
}

/** magic | salt | iterations (uint32 BE) | ciphertext of gzip(export JSON); the IV rides on the attachment row. */
export async function encryptBackup(json: string, key: CryptoKey, salt: Uint8Array, iterations: number, iv: Uint8Array): Promise<Uint8Array> {
  const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: iv as BufferSource }, key, gzipSync(strToU8(json)) as BufferSource))
  const out = new Uint8Array(MAGIC.length + SALT_BYTES + 4 + cipher.length)
  out.set(MAGIC, 0)
  out.set(salt, MAGIC.length)
  new DataView(out.buffer).setUint32(MAGIC.length + SALT_BYTES, iterations)
  out.set(cipher, MAGIC.length + SALT_BYTES + 4)
  return out
}

export async function decryptBackup(bytes: Uint8Array, passphrase: string, iv: Uint8Array): Promise<string> {
  if (bytes.length < MAGIC.length + SALT_BYTES + 4 || !MAGIC.every((b, i) => bytes[i] === b)) throw new Error("NOT_A_BACKUP")
  const salt = bytes.slice(MAGIC.length, MAGIC.length + SALT_BYTES)
  const iterations = new DataView(bytes.buffer, bytes.byteOffset).getUint32(MAGIC.length + SALT_BYTES)
  const key = await deriveKey(passphrase, salt, iterations)
  let plain: ArrayBuffer
  try {
    plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: iv as BufferSource }, key, bytes.slice(MAGIC.length + SALT_BYTES + 4) as BufferSource)
  } catch {
    throw new Error("WRONG_PASSPHRASE")
  }
  return strFromU8(gunzipSync(new Uint8Array(plain)))
}

/** Builds, encrypts and queues one backup; the sync engine uploads the bytes after the row. */
export async function createBackup(session: LocalSession): Promise<string> {
  const stored = await readMeta<StoredBackupKey>(META_KEYS.backupKey)
  if (!stored) throw new Error("BACKUP_NOT_CONFIGURED")
  const key = await crypto.subtle.importKey("jwk", stored.key, { name: "AES-GCM" }, true, ["encrypt", "decrypt"])
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES))
  const bytes = await encryptBackup(JSON.stringify(await buildExport(session)), key, new Uint8Array(stored.salt), stored.iterations, iv)
  const id = crypto.randomUUID()
  await db.transaction("rw", db.tables, async () => {
    await db.blobs.put({ id, bytes: bytes.buffer as ArrayBuffer, uploaded: false })
    await writeFields<Attachment>("attachment", id, {
      visibility: "PRIVATE", ownerType: BACKUP_OWNER_TYPE, ownerRecordId: session.userId, mimeType: MIME, byteSize: bytes.byteLength, iv: toBase64(iv), uploadedAt: null,
    })
    const older = (await listBackups()).filter((b) => b.id !== id).slice(KEEP_LAST - 1)
    for (const old of older) {
      await softDelete("attachment", old.id)
      await db.blobs.delete(old.id)
    }
  })
  await writeMeta(META_KEYS.backupLastAt, Date.now())
  return id
}

export async function listBackups(): Promise<Attachment[]> {
  const rows = await db.attachments.filter((a) => a.ownerType === BACKUP_OWNER_TYPE && a.deletedAt === null).toArray()
  return rows.sort((a, b) => ((a.uploadedAt ?? "9") < (b.uploadedAt ?? "9") ? 1 : -1))
}

export function useBackups(): Attachment[] {
  return useLiveQuery(listBackups, [], [])
}

let running = false

/** Called after a successful sync: at most one automatic backup a day, and only when configured. */
export async function runBackupIfDue(session: LocalSession | null) {
  if (!session || running) return
  const state = await backupState()
  if (!state.configured || !state.automatic) return
  if (state.lastBackupAt !== null && Date.now() - state.lastBackupAt < DAY_MS) return
  running = true
  try {
    await createBackup(session)
  } finally {
    running = false
  }
}

/** Fetches (or reads locally), decrypts and validates; the caller shows the preview and then applies it. */
export async function openBackup(attachment: Attachment, passphrase: string): Promise<ImportPreview> {
  let blob = await db.blobs.get(attachment.id)
  if (!blob) {
    const { url } = await unwrap(api.GET("/api/v1/attachments/{id}/download-url", { params: { path: { id: attachment.id } } }))
    const response = await fetch(url)
    if (!response.ok) throw new Error("BACKUP_DOWNLOAD_FAILED")
    blob = { id: attachment.id, bytes: await response.arrayBuffer(), uploaded: true }
    await db.blobs.put(blob)
  }
  const json = await decryptBackup(new Uint8Array(blob.bytes), passphrase, fromBase64(attachment.iv))
  return validateImport(JSON.parse(json))
}
