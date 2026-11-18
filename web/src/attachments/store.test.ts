import { beforeEach, describe, expect, it } from "vitest"

import { db, type Attachment } from "@/db/schema"
import { BACKUP_OWNER_TYPE, dropExpiredPhotos, photoExpired } from "./store"

const NOW = Date.parse("2026-11-18T12:00:00Z")
const daysAgo = (days: number) => new Date(NOW - days * 86_400_000).toISOString()

function attachment(id: string, ownerType: string, uploadedAt: string | null): Attachment {
  return {
    id, ownerType, uploadedAt, visibility: "SHARED", ownerRecordId: "t1", mimeType: "image/jpeg", byteSize: 10, iv: "aXY=", blobKey: null, deletedAt: null,
  } as Attachment
}

describe("receipt photo retention", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it("a receipt photo expires 30 days after upload; backups and unsent photos never do", () => {
    expect(photoExpired(attachment("a", "TRANSACTION", daysAgo(29)), NOW)).toBe(false)
    expect(photoExpired(attachment("a", "TRANSACTION", daysAgo(30)), NOW)).toBe(true)
    expect(photoExpired(attachment("a", BACKUP_OWNER_TYPE, daysAgo(90)), NOW)).toBe(false)
    expect(photoExpired(attachment("a", "TRANSACTION", null), NOW)).toBe(false)
  })

  it("drops only this device's copies of expired photos and keeps every row", async () => {
    await db.attachments.bulkPut([
      attachment("old", "TRANSACTION", daysAgo(40)),
      attachment("new", "TRANSACTION", daysAgo(3)),
      attachment("backup", BACKUP_OWNER_TYPE, daysAgo(40)),
    ])
    await db.blobs.bulkPut(["old", "new", "backup"].map((id) => ({ id, bytes: new ArrayBuffer(4), uploaded: true })))

    expect(await dropExpiredPhotos(NOW)).toBe(1)

    expect((await db.blobs.toCollection().primaryKeys()).sort()).toEqual(["backup", "new"])
    expect(await db.attachments.count()).toBe(3)
  })
})
