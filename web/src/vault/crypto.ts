/**
 * ADR e2e-1. Everything the server stores about a record is sealed here first; the server only
 * ever sees ciphertext, wrapped keys, and an auth key it cannot turn back into the password.
 */

export type KeyKind = "HOUSEHOLD" | "REPORT"
export type SealKind = "h" | "u" | "r"

const PBKDF2_ITERATIONS = 600_000
const AES_BITS = 256
const IV_BYTES = 12
const PAD_BLOCK = 32
const PAD_MARKER = 0x80
const CIPHER_PREFIX = "e1"
const DEFAULT_COUNTRY_CODE = "+964"
const encoder = new TextEncoder()
const decoder = new TextDecoder()

export function toBase64Url(bytes: ArrayBuffer | Uint8Array): string {
  const view = new Uint8Array(bytes)
  let text = ""
  for (let i = 0; i < view.length; i += 0x8000) text += String.fromCharCode(...view.subarray(i, i + 0x8000))
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

export function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  const padded = text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4)
  const raw = atob(padded)
  const bytes = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes
}

function concat(...parts: Uint8Array[]): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(new ArrayBuffer(parts.reduce((n, p) => n + p.length, 0)))
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

/** Mirrors the server's Identifier.parse, so every device derives the same salt for one account. */
export function normaliseIdentifier(raw: string): string {
  const trimmed = raw.trim()
  if (trimmed.includes("@")) return trimmed.toLowerCase()
  const digits = trimmed.replace(/[\s\-()]/g, "")
  if (/^0\d{8,10}$/.test(digits)) return DEFAULT_COUNTRY_CODE + digits.slice(1)
  return digits
}

async function hkdfBits(material: Uint8Array<ArrayBuffer>, info: string, salt: Uint8Array<ArrayBuffer> = new Uint8Array(new ArrayBuffer(0))): Promise<Uint8Array<ArrayBuffer>> {
  const key = await crypto.subtle.importKey("raw", material, "HKDF", false, ["deriveBits"])
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt, info: encoder.encode(info) }, key, AES_BITS))
}

async function wrappingKey(secret: Uint8Array<ArrayBuffer>): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", secret, "AES-KW", false, ["wrapKey", "unwrapKey"])
}

export interface PasswordKeys {
  authKey: string
  wrapSecret: Uint8Array<ArrayBuffer>
}

/** One slow stretch, then two independent keys: one proves the password, the other never leaves. */
export async function derivePasswordKeys(identifier: string, password: string): Promise<PasswordKeys> {
  const salt = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(`mizan-account|${normaliseIdentifier(identifier)}`)))
  const material = await crypto.subtle.importKey("raw", encoder.encode(password.normalize("NFKC")), "PBKDF2", false, ["deriveBits"])
  const stretched = new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: PBKDF2_ITERATIONS }, material, AES_BITS))
  const [auth, wrap] = await Promise.all([hkdfBits(stretched, "mizan auth key"), hkdfBits(stretched, "mizan wrapping key")])
  return { authKey: toBase64Url(auth), wrapSecret: wrap }
}

const RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const RECOVERY_GROUP = 4

/** 160 random bits as 32 unambiguous characters, grouped for writing down. */
export function newRecoveryCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(20))
  let bits = 0
  let value = 0
  let out = ""
  for (const byte of bytes) {
    value = (value << 8) | byte
    bits += 8
    while (bits >= 5) {
      out += RECOVERY_ALPHABET[(value >>> (bits - 5)) & 31]
      bits -= 5
    }
  }
  return out.match(new RegExp(`.{1,${RECOVERY_GROUP}}`, "g"))!.join("-")
}

export function cleanRecoveryCode(code: string): string {
  return code.toUpperCase().replace(/[^A-Z0-9]/g, "")
}

async function recoverySecret(code: string): Promise<Uint8Array<ArrayBuffer>> {
  return hkdfBits(encoder.encode(cleanRecoveryCode(code)), "mizan recovery key")
}

async function newAesKey(): Promise<CryptoKey> {
  return crypto.subtle.generateKey({ name: "AES-GCM", length: AES_BITS }, true, ["encrypt", "decrypt"])
}

async function wrapAes(key: CryptoKey, secret: Uint8Array<ArrayBuffer>): Promise<string> {
  return toBase64Url(await crypto.subtle.wrapKey("raw", key, await wrappingKey(secret), "AES-KW"))
}

async function unwrapAes(wrapped: string, secret: Uint8Array<ArrayBuffer>): Promise<CryptoKey | null> {
  try {
    return await crypto.subtle.unwrapKey("raw", fromBase64Url(wrapped), await wrappingKey(secret), "AES-KW",
      { name: "AES-GCM", length: AES_BITS }, true, ["encrypt", "decrypt"])
  } catch {
    return null
  }
}

async function encryptBytes(key: CryptoKey, plain: Uint8Array<ArrayBuffer>, aad?: Uint8Array<ArrayBuffer>): Promise<Uint8Array<ArrayBuffer>> {
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES))
  const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, ...(aad ? { additionalData: aad } : {}) }, key, plain))
  return concat(iv, cipher)
}

async function decryptBytes(key: CryptoKey, sealed: Uint8Array<ArrayBuffer>, aad?: Uint8Array<ArrayBuffer>): Promise<Uint8Array<ArrayBuffer>> {
  const iv = sealed.slice(0, IV_BYTES)
  return new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv, ...(aad ? { additionalData: aad } : {}) }, key, sealed.slice(IV_BYTES)))
}

export interface AccountKeyMaterial {
  wrappedByPassword: string
  wrappedByRecovery: string
  publicKey: string
  encryptedPrivateKey: string
}

export interface OpenAccount {
  userKey: CryptoKey
  privateKey: CryptoKey
  publicKey: string
}

async function importPrivate(pkcs8: Uint8Array<ArrayBuffer>): Promise<CryptoKey> {
  return crypto.subtle.importKey("pkcs8", pkcs8, { name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"])
}

/** A fresh account: the user key wrapped twice, and an ECDH pair whose private half the user key seals. */
export async function createAccountKeys(wrapSecret: Uint8Array<ArrayBuffer>): Promise<{ material: AccountKeyMaterial; recoveryCode: string; account: OpenAccount }> {
  const userKey = await newAesKey()
  const recoveryCode = newRecoveryCode()
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]) as CryptoKeyPair
  const publicKey = toBase64Url(await crypto.subtle.exportKey("spki", pair.publicKey))
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey))
  const material: AccountKeyMaterial = {
    wrappedByPassword: await wrapAes(userKey, wrapSecret),
    wrappedByRecovery: await wrapAes(userKey, await recoverySecret(recoveryCode)),
    publicKey,
    encryptedPrivateKey: toBase64Url(await encryptBytes(userKey, pkcs8, encoder.encode("mizan private key"))),
  }
  return { material, recoveryCode, account: { userKey, privateKey: await importPrivate(pkcs8), publicKey } }
}

async function openWithUserKey(material: AccountKeyMaterial, userKey: CryptoKey): Promise<OpenAccount> {
  const pkcs8 = await decryptBytes(userKey, fromBase64Url(material.encryptedPrivateKey), encoder.encode("mizan private key"))
  return { userKey, privateKey: await importPrivate(pkcs8), publicKey: material.publicKey }
}

export async function openAccountWithPassword(material: AccountKeyMaterial, wrapSecret: Uint8Array<ArrayBuffer>): Promise<OpenAccount | null> {
  const userKey = await unwrapAes(material.wrappedByPassword, wrapSecret)
  return userKey ? openWithUserKey(material, userKey) : null
}

export async function openAccountWithRecovery(material: AccountKeyMaterial, code: string): Promise<OpenAccount | null> {
  const userKey = await unwrapAes(material.wrappedByRecovery, await recoverySecret(code))
  return userKey ? openWithUserKey(material, userKey) : null
}

export async function wrapUserKeyForPassword(userKey: CryptoKey, wrapSecret: Uint8Array<ArrayBuffer>): Promise<string> {
  return wrapAes(userKey, wrapSecret)
}

export async function wrapUserKeyForRecovery(userKey: CryptoKey, code: string): Promise<string> {
  return wrapAes(userKey, await recoverySecret(code))
}

export async function newSharedKey(): Promise<CryptoKey> {
  return newAesKey()
}

function grantInfo(kind: KeyKind, version: number): string {
  return `mizan grant|${kind}|${version}`
}

async function grantKey(shared: Uint8Array<ArrayBuffer>, ephemeral: Uint8Array<ArrayBuffer>, kind: KeyKind, version: number): Promise<CryptoKey> {
  const bits = await hkdfBits(shared, grantInfo(kind, version), ephemeral)
  return crypto.subtle.importKey("raw", bits, "AES-GCM", false, ["encrypt", "decrypt"])
}

/** Seals a household or report key to one member's public key with a throwaway ECDH pair. */
export async function wrapForMember(key: CryptoKey, memberPublicKey: string, kind: KeyKind, version: number): Promise<string> {
  const recipient = await crypto.subtle.importKey("spki", fromBase64Url(memberPublicKey), { name: "ECDH", namedCurve: "P-256" }, false, [])
  const ephemeral = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]) as CryptoKeyPair
  const ephemeralRaw = new Uint8Array(await crypto.subtle.exportKey("raw", ephemeral.publicKey))
  const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: recipient }, ephemeral.privateKey, AES_BITS))
  const raw = new Uint8Array(await crypto.subtle.exportKey("raw", key))
  const sealed = await encryptBytes(await grantKey(shared, ephemeralRaw, kind, version), raw, encoder.encode(grantInfo(kind, version)))
  return toBase64Url(concat(ephemeralRaw, sealed))
}

const P256_RAW_BYTES = 65

export async function openGrant(wrapped: string, privateKey: CryptoKey, kind: KeyKind, version: number): Promise<CryptoKey | null> {
  try {
    const bytes = fromBase64Url(wrapped)
    const ephemeralRaw = bytes.slice(0, P256_RAW_BYTES)
    const ephemeral = await crypto.subtle.importKey("raw", ephemeralRaw, { name: "ECDH", namedCurve: "P-256" }, false, [])
    const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: ephemeral }, privateKey, AES_BITS))
    const raw = await decryptBytes(await grantKey(shared, ephemeralRaw, kind, version), bytes.slice(P256_RAW_BYTES), encoder.encode(grantInfo(kind, version)))
    return crypto.subtle.importKey("raw", raw, { name: "AES-GCM", length: AES_BITS }, true, ["encrypt", "decrypt"])
  } catch {
    return null
  }
}

function fieldAad(recordId: string, table: string, field: string): Uint8Array<ArrayBuffer> {
  return encoder.encode(`${CIPHER_PREFIX}|${table}|${recordId}|${field}`)
}

/** Pads to a block so the length of a ciphertext says little about the amount or name inside. */
function pad(plain: Uint8Array<ArrayBuffer>): Uint8Array<ArrayBuffer> {
  const length = Math.ceil((plain.length + 1) / PAD_BLOCK) * PAD_BLOCK
  const out = new Uint8Array(new ArrayBuffer(length))
  out.set(plain)
  out[plain.length] = PAD_MARKER
  return out
}

function unpad(padded: Uint8Array<ArrayBuffer>): Uint8Array<ArrayBuffer> {
  const end = padded.lastIndexOf(PAD_MARKER)
  if (end < 0) throw new Error("VAULT_BAD_PADDING")
  return padded.slice(0, end)
}

export interface SealTarget {
  kind: SealKind
  version: number
  key: CryptoKey
}

/** The AAD binds the value to its record, table and field, so the server cannot move it elsewhere. */
export async function sealField(target: SealTarget, table: string, recordId: string, field: string, value: unknown): Promise<string> {
  const plain = pad(encoder.encode(JSON.stringify(value ?? null)))
  const sealed = await encryptBytes(target.key, plain, fieldAad(recordId, table, field))
  return `${CIPHER_PREFIX}.${target.kind}.${target.version}.${toBase64Url(sealed)}`
}

export interface SealedHeader {
  kind: SealKind
  version: number
  body: string
}

export function readHeader(text: unknown): SealedHeader | null {
  if (typeof text !== "string") return null
  const match = /^e1\.([hur])\.(\d{1,4})\.([A-Za-z0-9_-]+)$/.exec(text)
  return match ? { kind: match[1] as SealKind, version: Number(match[2]), body: match[3] } : null
}

export async function openField(key: CryptoKey, header: SealedHeader, table: string, recordId: string, field: string): Promise<unknown> {
  const plain = await decryptBytes(key, fromBase64Url(header.body), fieldAad(recordId, table, field))
  return JSON.parse(decoder.decode(unpad(plain)))
}

/** A stable id from stable inputs, so devices that create "the same" record converge on one row. */
export async function deterministicId(...parts: string[]): Promise<string> {
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(parts.join("|")))).slice(0, 16)
  hash[6] = (hash[6] & 0x0f) | 0x50
  hash[8] = (hash[8] & 0x3f) | 0x80
  const hex = [...hash].map((b) => b.toString(16).padStart(2, "0")).join("")
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
