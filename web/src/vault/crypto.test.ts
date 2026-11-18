import { describe, expect, it } from "vitest"

import {
  createAccountKeys, derivePasswordKeys, deterministicId, newSharedKey, normaliseIdentifier, openAccountWithPassword,
  openAccountWithRecovery, openField, openGrant, readHeader, sealField, wrapForMember, wrapUserKeyForPassword, toBase64Url, fromBase64Url,
} from "./crypto"

async function deriveFirstAndOpen(identifier: string, password: string) {
  const { wrapSecret } = await derivePasswordKeys(identifier, password)
  return (await createAccountKeys(wrapSecret)).account
}

describe("identifier normalisation", () => {
  it("matches the server's rules so every device derives the same salt", () => {
    expect(normaliseIdentifier("Ridha@Example.IQ")).toBe("ridha@example.iq")
    expect(normaliseIdentifier("0770 123 4567")).toBe("+9647701234567")
    expect(normaliseIdentifier("+964 (770) 123-4567")).toBe("+9647701234567")
  })
})

describe("password keys", () => {
  it("are deterministic per account and password, and the auth key never equals the wrapping key", async () => {
    const a = await derivePasswordKeys("ridha@example.iq", "correct horse")
    const b = await derivePasswordKeys("RIDHA@example.iq", "correct horse")
    const c = await derivePasswordKeys("ridha@example.iq", "wrong horse")
    expect(a.authKey).toBe(b.authKey)
    expect(a.authKey).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(a.authKey).not.toBe(c.authKey)
    expect(toBase64Url(a.wrapSecret)).not.toBe(a.authKey)
  })
})

describe("account keys", () => {
  it("open with the password or the recovery code, and not with a wrong one", async () => {
    const { wrapSecret } = await derivePasswordKeys("ridha@example.iq", "pw-one")
    const created = await createAccountKeys(wrapSecret)
    expect(created.recoveryCode).toMatch(/^[A-Z2-9]{4}(-[A-Z2-9]{4}){7}$/)

    const byPassword = await openAccountWithPassword(created.material, wrapSecret)
    expect(byPassword?.publicKey).toBe(created.material.publicKey)
    expect(await openAccountWithRecovery(created.material, created.recoveryCode.toLowerCase())).not.toBeNull()

    const other = await derivePasswordKeys("ridha@example.iq", "pw-two")
    expect(await openAccountWithPassword(created.material, other.wrapSecret)).toBeNull()
    expect(await openAccountWithRecovery(created.material, "AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA")).toBeNull()
  })

  it("re-wraps under a new password after recovery", async () => {
    const first = await derivePasswordKeys("noor@example.iq", "old")
    const created = await createAccountKeys(first.wrapSecret)
    const recovered = await openAccountWithRecovery(created.material, created.recoveryCode)
    const next = await derivePasswordKeys("noor@example.iq", "new")
    const rewrapped = { ...created.material, wrappedByPassword: await wrapUserKeyForPassword(recovered!.userKey, next.wrapSecret) }
    expect(await openAccountWithPassword(rewrapped, next.wrapSecret)).not.toBeNull()
  })
})

describe("grants", () => {
  it("only the addressed member can open a household key, and only for that kind and version", async () => {
    const noor = await deriveFirstAndOpen("noor@example.iq", "pw")
    const sara = await deriveFirstAndOpen("sara@example.iq", "pw")
    const household = await newSharedKey()
    const wrapped = await wrapForMember(household, noor.publicKey, "HOUSEHOLD", 1)

    const opened = await openGrant(wrapped, noor.privateKey, "HOUSEHOLD", 1)
    expect(opened).not.toBeNull()
    expect(await openGrant(wrapped, sara.privateKey, "HOUSEHOLD", 1)).toBeNull()
    expect(await openGrant(wrapped, noor.privateKey, "REPORT", 1)).toBeNull()
    expect(await openGrant(wrapped, noor.privateKey, "HOUSEHOLD", 2)).toBeNull()

    const sealed = await sealField({ kind: "h", version: 1, key: household }, "cash_account", "row-1", "balance", 1_250_000)
    expect(await openField(opened!, readHeader(sealed)!, "cash_account", "row-1", "balance")).toBe(1_250_000)
  })
})

describe("sealed fields", () => {
  it("carry the server's shape, hide the value and its length, and refuse to move", async () => {
    const key = await newSharedKey()
    const target = { kind: "h" as const, version: 3, key }
    const short = await sealField(target, "cash_account", "row-1", "name", "W")
    const longer = await sealField(target, "cash_account", "row-1", "name", "Wallet at home")
    expect(short).toMatch(/^e1\.h\.3\.[A-Za-z0-9_-]{24,}$/)
    expect(new TextDecoder("latin1").decode(fromBase64Url(longer.split(".")[3]))).not.toContain("Wallet")
    expect(short.length).toBe(longer.length)

    const header = readHeader(short)!
    expect(await openField(key, header, "cash_account", "row-1", "name")).toBe("W")
    await expect(openField(key, header, "cash_account", "row-2", "name")).rejects.toThrow()
    await expect(openField(key, header, "cash_account", "row-1", "institution")).rejects.toThrow()
    await expect(openField(key, header, "debt", "row-1", "name")).rejects.toThrow()
  })

  it("round-trip objects, nulls and booleans", async () => {
    const target = { kind: "u" as const, version: 1, key: await newSharedKey() }
    for (const value of [null, true, 0, { shares: [{ assetClass: "CASH", amount: 5 }], illiquidAssets: 0 }]) {
      const sealed = await sealField(target, "member_contribution", "r", "composition", value)
      expect(await openField(target.key, readHeader(sealed)!, "member_contribution", "r", "composition")).toEqual(value)
    }
  })
})

describe("deterministic ids", () => {
  it("give two devices the same row for the same thing", async () => {
    const a = await deterministicId("annual_report", "household", "2026")
    expect(a).toBe(await deterministicId("annual_report", "household", "2026"))
    expect(a).not.toBe(await deterministicId("annual_report", "household", "2025"))
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })
})
