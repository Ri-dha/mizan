import { describe, expect, it } from "vitest"

import { decryptBackup, encryptBackup } from "./store"

const salt = new Uint8Array(16).fill(7)
const iv = new Uint8Array(12).fill(3)

async function keyFor(passphrase: string) {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"])
  return crypto.subtle.deriveKey({ name: "PBKDF2", salt, iterations: 1000, hash: "SHA-256" }, material, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"])
}

describe("encrypted backup", () => {
  it("round-trips through the passphrase and refuses the wrong one", async () => {
    const json = JSON.stringify({ format: "mizan-export", tables: { cash_account: [{ id: "a", name: "محفظة" }] } })
    const bytes = await encryptBackup(json, await keyFor("correct horse"), salt, 1000, iv)

    expect(new TextDecoder().decode(bytes.slice(0, 4))).toBe("MZB1")
    await expect(decryptBackup(bytes, "correct horse", iv)).resolves.toBe(json)
    await expect(decryptBackup(bytes, "wrong", iv)).rejects.toThrow("WRONG_PASSPHRASE")
    await expect(decryptBackup(new Uint8Array([1, 2, 3]), "x", iv)).rejects.toThrow("NOT_A_BACKUP")
  })
})
