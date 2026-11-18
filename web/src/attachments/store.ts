import { api, unwrap } from "@/api/client"
import { db, type Attachment } from "@/db/schema"
import { softDelete, writeFields } from "@/db/write"
import { decryptBytes, encryptBytes } from "./crypto"
import { downscaleImage } from "./images"

const JPEG = "image/jpeg"
export const BACKUP_OWNER_TYPE = "BACKUP"
/** ADR storage-2: the server deletes receipt photos this long after upload; keep in step with `mizan.storage.receipt-retention`. */
export const RECEIPT_RETENTION_DAYS = 30
const DAY_MS = 86_400_000
/** Storage only ever holds ciphertext, so it is never told what kind of file it is. */
const SEALED_CONTENT_TYPE = "application/octet-stream"

/** Downscale, encrypt, keep locally, and queue the metadata row for sync; bytes upload after it. */
export async function attachImage(file: File, ownerType: string, ownerRecordId: string): Promise<string> {
  const image = await downscaleImage(file)
  const { iv, cipher, blobKey } = await encryptBytes(await image.arrayBuffer())
  const id = crypto.randomUUID()
  await db.transaction("rw", db.tables, async () => {
    await db.blobs.put({ id, bytes: cipher, uploaded: false })
    await writeFields<Attachment>("attachment", id, {
      visibility: "SHARED", ownerType, ownerRecordId, mimeType: JPEG, byteSize: cipher.byteLength, iv, blobKey, uploadedAt: null,
    })
  })
  return id
}

export async function removeAttachment(id: string) {
  await db.transaction("rw", db.tables, async () => {
    await db.blobs.delete(id)
    await softDelete("attachment", id)
  })
}

/** The decrypted bytes of an attachment held on this device, for on-device processing such as OCR. */
export async function attachmentBlob(id: string): Promise<Blob | null> {
  const attachment = await db.attachments.get(id)
  const blob = await db.blobs.get(id)
  if (!attachment || !blob) return null
  return new Blob([await decryptBytes(blob.bytes, attachment.iv, attachment.blobKey)], { type: attachment.mimeType })
}

type Kind = "RECEIPT" | "BACKUP"
const kindOf = (attachment: Attachment): Kind => (attachment.ownerType === BACKUP_OWNER_TYPE ? "BACKUP" : "RECEIPT")

/** Counted from the upload, as the server counts it; a photo not uploaded yet has not started its month. */
export function photoExpired(attachment: Pick<Attachment, "ownerType" | "uploadedAt">, now = Date.now()): boolean {
  if (attachment.ownerType === BACKUP_OWNER_TYPE || !attachment.uploadedAt) return false
  return now - Date.parse(attachment.uploadedAt) >= RECEIPT_RETENTION_DAYS * DAY_MS
}

/** The row stays so the transaction keeps its link; only this device's copy of the bytes goes. */
export async function dropExpiredPhotos(now = Date.now()): Promise<number> {
  const expired = await db.attachments.filter((a) => a.deletedAt === null && photoExpired(a, now)).primaryKeys()
  const held = (await db.blobs.bulkGet(expired)).filter((blob) => blob !== undefined).map((blob) => blob.id)
  await db.blobs.bulkDelete(held)
  return held.length
}

/** A decrypted object URL for display; fetches from storage when this device has no copy. */
export async function attachmentObjectUrl(id: string): Promise<string | null> {
  const attachment = await db.attachments.get(id)
  if (!attachment || attachment.deletedAt !== null || photoExpired(attachment)) return null
  let blob = await db.blobs.get(id)
  if (!blob) {
    if (!navigator.onLine) return null
    const { url } = await unwrap(api.GET("/api/v1/attachments/{id}/download-url", { params: { path: { id }, query: { kind: kindOf(attachment) } } }))
    const response = await fetch(url)
    if (!response.ok) return null
    blob = { id, bytes: await response.arrayBuffer(), uploaded: true }
    await db.blobs.put(blob)
  }
  const plain = await decryptBytes(blob.bytes, attachment.iv, attachment.blobKey)
  return URL.createObjectURL(new Blob([plain], { type: attachment.mimeType }))
}

/** Runs after a push, so the row the URL is signed against already exists on the server. */
export async function uploadPendingBlobs(): Promise<void> {
  const pending = await db.blobs.filter((b) => !b.uploaded).toArray()
  for (const blob of pending) {
    const stillQueued = await db.outbox.where("rowId").equals(blob.id).count()
    if (stillQueued > 0) continue
    const attachment = await db.attachments.get(blob.id)
    if (!attachment || attachment.deletedAt !== null) continue
    const { url } = await unwrap(api.POST("/api/v1/attachments/{id}/upload-url", { params: { path: { id: blob.id }, query: { kind: kindOf(attachment) } } }))
    const response = await fetch(url, { method: "PUT", body: blob.bytes, headers: { "Content-Type": SEALED_CONTENT_TYPE } })
    if (!response.ok) continue
    await db.blobs.update(blob.id, { uploaded: true })
    await writeFields<Attachment>("attachment", blob.id, { uploadedAt: new Date().toISOString() })
  }
}
