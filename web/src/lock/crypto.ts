const PBKDF2_ITERATIONS = 600_000
const KEY_LENGTH_BITS = 256

export interface WrappedKey {
  salt: string
  wrapped: string
}

function toBase64(bytes: ArrayBuffer | Uint8Array): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
}

function fromBase64(text: string): Uint8Array {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0))
}

export async function generateDataKey(): Promise<CryptoKey> {
  return crypto.subtle.generateKey({ name: "AES-GCM", length: KEY_LENGTH_BITS }, true, ["encrypt", "decrypt"])
}

async function wrappingKeyFromSecret(secret: Uint8Array, salt: Uint8Array): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey("raw", secret as BufferSource, "PBKDF2", false, ["deriveKey"])
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", salt: salt as BufferSource, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
    material,
    { name: "AES-KW", length: KEY_LENGTH_BITS },
    false,
    ["wrapKey", "unwrapKey"],
  )
}

/** Wraps the data key under a secret (PIN digits or a WebAuthn PRF output). */
export async function wrapDataKey(dataKey: CryptoKey, secret: Uint8Array): Promise<WrappedKey> {
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const wrappingKey = await wrappingKeyFromSecret(secret, salt)
  const wrapped = await crypto.subtle.wrapKey("raw", dataKey, wrappingKey, "AES-KW")
  return { salt: toBase64(salt), wrapped: toBase64(wrapped) }
}

/** Returns null when the secret is wrong: AES-KW fails integrity rather than yielding garbage. */
export async function unwrapDataKey(entry: WrappedKey, secret: Uint8Array): Promise<CryptoKey | null> {
  const wrappingKey = await wrappingKeyFromSecret(secret, fromBase64(entry.salt))
  try {
    return await crypto.subtle.unwrapKey(
      "raw", fromBase64(entry.wrapped) as BufferSource, wrappingKey, "AES-KW",
      { name: "AES-GCM", length: KEY_LENGTH_BITS }, true, ["encrypt", "decrypt"],
    )
  } catch {
    return null
  }
}

export function secretFromPin(pin: string): Uint8Array {
  return new TextEncoder().encode(pin)
}
