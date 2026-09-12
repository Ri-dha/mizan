const MAX_EDGE_PIXELS = 1600
const JPEG_QUALITY = 0.82

/** Phones shoot several megabytes per receipt; 1600 px is plenty to read one. Re-encoding also drops EXIF. */
export async function downscaleImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_EDGE_PIXELS / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement("canvas")
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not encode image"))), "image/jpeg", JPEG_QUALITY),
  )
}
