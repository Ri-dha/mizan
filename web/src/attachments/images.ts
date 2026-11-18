const MAX_EDGE_PIXELS = 1200
const JPEG_QUALITY = 0.6

/**
 * Receipts are read, not admired: 1200 px in grey keeps every digit legible for people and OCR
 * at about a quarter of a colour 1600 px photo. Re-encoding also drops EXIF. The grey conversion
 * is done on the pixels because Safari ignores the canvas `filter` property.
 */
export async function downscaleImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_EDGE_PIXELS / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement("canvas")
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const context = canvas.getContext("2d")!
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
  toGrey(pixels.data)
  context.putImageData(pixels, 0, 0)
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not encode image"))), "image/jpeg", JPEG_QUALITY),
  )
}

/** Rec. 601 luma, the weighting JPEG itself uses, in place over RGBA bytes. */
export function toGrey(rgba: Uint8ClampedArray) {
  for (let i = 0; i < rgba.length; i += 4) {
    const luma = Math.round(0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2])
    rgba[i] = rgba[i + 1] = rgba[i + 2] = luma
  }
}
