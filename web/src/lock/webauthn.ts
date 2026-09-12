import { META_KEYS, readMeta, writeMeta, deleteMeta } from "@/db/meta"
import { unwrapDataKey, wrapDataKey, type WrappedKey } from "./crypto"

interface WebAuthnEntry extends WrappedKey {
  credentialId: string
}

const PRF_SALT = new TextEncoder().encode("mizan-lock-v1")

type PrfExtension = { prf?: { eval?: { first: BufferSource }; results?: { first?: ArrayBuffer }; enabled?: boolean } }

function toBase64Url(bytes: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

function fromBase64Url(text: string): Uint8Array {
  const padded = text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4)
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
}

export async function webAuthnAvailable(): Promise<boolean> {
  if (!("PublicKeyCredential" in window)) return false
  try {
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

export async function hasWebAuthnUnlock(): Promise<boolean> {
  return (await readMeta<WebAuthnEntry>(META_KEYS.webauthn)) !== undefined
}

/**
 * Enrols a platform credential and wraps the data key under its PRF output, so a biometric
 * prompt yields the same key the PIN does. Returns false where the authenticator has no PRF.
 */
export async function enrollWebAuthn(dataKey: CryptoKey, userId: string, displayName: string): Promise<boolean> {
  const credential = (await navigator.credentials.create({
    publicKey: {
      challenge: crypto.getRandomValues(new Uint8Array(32)),
      rp: { name: "Mizan" },
      user: { id: new TextEncoder().encode(userId), name: displayName, displayName },
      pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
      authenticatorSelection: { authenticatorAttachment: "platform", userVerification: "required", residentKey: "preferred" },
      extensions: { prf: { eval: { first: PRF_SALT } } } as AuthenticationExtensionsClientInputs & PrfExtension,
    },
  })) as PublicKeyCredential | null
  if (!credential) return false

  const results = (credential.getClientExtensionResults() as PrfExtension).prf
  const secret = results?.results?.first
  if (!secret) return false

  const wrapped = await wrapDataKey(dataKey, new Uint8Array(secret))
  await writeMeta(META_KEYS.webauthn, { ...wrapped, credentialId: toBase64Url(credential.rawId) } satisfies WebAuthnEntry)
  return true
}

export async function unlockWithWebAuthn(): Promise<CryptoKey | null> {
  const entry = await readMeta<WebAuthnEntry>(META_KEYS.webauthn)
  if (!entry) return null

  const assertion = (await navigator.credentials.get({
    publicKey: {
      challenge: crypto.getRandomValues(new Uint8Array(32)),
      allowCredentials: [{ type: "public-key", id: fromBase64Url(entry.credentialId) as BufferSource }],
      userVerification: "required",
      extensions: { prf: { eval: { first: PRF_SALT } } } as AuthenticationExtensionsClientInputs & PrfExtension,
    },
  })) as PublicKeyCredential | null
  const secret = (assertion?.getClientExtensionResults() as PrfExtension | undefined)?.prf?.results?.first
  if (!secret) return null
  return unwrapDataKey(entry, new Uint8Array(secret))
}

export async function removeWebAuthn() {
  await deleteMeta(META_KEYS.webauthn)
}
