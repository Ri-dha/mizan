import { META_KEYS, readMeta, writeMeta } from "@/db/meta"
import { currentDataKey } from "@/lock/store"

const RAW_KEY = "attachments.rawKey"
const IV_BYTES = 12

function toBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
}

export function fromBase64(text: string): Uint8Array {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0))
}

/**
 * The key receipts are encrypted with. Behind a PIN it is the wrapped data key; without one
 * it has to live unwrapped on the device, which is the trade-off of skipping the lock.
 */
export async function ensureDataKey(): Promise<CryptoKey> {
  const locked = currentDataKey()
  if (locked) return locked
  const stored = await readMeta<JsonWebKey>(RAW_KEY)
  if (stored) return crypto.subtle.importKey("jwk", stored, { name: "AES-GCM" }, true, ["encrypt", "decrypt"])
  const key = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"])
  await writeMeta(RAW_KEY, await crypto.subtle.exportKey("jwk", key))
  return key
}

export async function encryptBytes(plain: ArrayBuffer): Promise<{ iv: string; cipher: ArrayBuffer }> {
  const key = await ensureDataKey()
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES))
  const cipher = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, plain)
  return { iv: toBase64(iv), cipher }
}

export async function decryptBytes(cipher: ArrayBuffer, iv: string): Promise<ArrayBuffer> {
  const key = await ensureDataKey()
  return crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64(iv) as BufferSource }, key, cipher)
}

export { META_KEYS }
