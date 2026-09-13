import { api, unwrap } from "@/api/client"
import { db, type Attachment } from "@/db/schema"
import { softDelete, writeFields } from "@/db/write"
import { decryptBytes, encryptBytes } from "./crypto"
import { downscaleImage } from "./images"

const JPEG = "image/jpeg"

/** Downscale, encrypt, keep locally, and queue the metadata row for sync; bytes upload after it. */
export async function attachImage(file: File, ownerType: string, ownerRecordId: string): Promise<string> {
  const image = await downscaleImage(file)
  const { iv, cipher } = await encryptBytes(await image.arrayBuffer())
  const id = crypto.randomUUID()
  await db.transaction("rw", db.tables, async () => {
    await db.blobs.put({ id, bytes: cipher, uploaded: false })
    await writeFields<Attachment>("attachment", id, {
      visibility: "SHARED", ownerType, ownerRecordId, mimeType: JPEG, byteSize: cipher.byteLength, iv, uploadedAt: null,
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
  return new Blob([await decryptBytes(blob.bytes, attachment.iv)], { type: attachment.mimeType })
}

/** A decrypted object URL for display; fetches from storage when this device has no copy. */
export async function attachmentObjectUrl(id: string): Promise<string | null> {
  const attachment = await db.attachments.get(id)
  if (!attachment || attachment.deletedAt !== null) return null
  let blob = await db.blobs.get(id)
  if (!blob) {
    if (!navigator.onLine) return null
    const { url } = await unwrap(api.GET("/api/v1/attachments/{id}/download-url", { params: { path: { id } } }))
    const response = await fetch(url)
    if (!response.ok) return null
    blob = { id, bytes: await response.arrayBuffer(), uploaded: true }
    await db.blobs.put(blob)
  }
  const plain = await decryptBytes(blob.bytes, attachment.iv)
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
    const { url } = await unwrap(api.POST("/api/v1/attachments/{id}/upload-url", { params: { path: { id: blob.id } } }))
    const response = await fetch(url, { method: "PUT", body: blob.bytes, headers: { "Content-Type": attachment.mimeType } })
    if (!response.ok) continue
    await db.blobs.update(blob.id, { uploaded: true })
    await writeFields<Attachment>("attachment", blob.id, { uploadedAt: new Date().toISOString() })
  }
}
