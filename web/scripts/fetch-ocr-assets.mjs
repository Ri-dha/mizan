// Copies the Tesseract worker and core from node_modules and downloads the fast English and
// Arabic models into public/ocr, so receipt reading works offline and never leaves the device.
// Skips anything already present; a failed download is reported but does not fail the build.
import { copyFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs"
import { join, resolve } from "node:path"

const out = resolve(process.cwd(), "public/ocr")
mkdirSync(out, { recursive: true })

const copies = [
  ["node_modules/tesseract.js/dist/worker.min.js", "worker.min.js"],
  ["node_modules/tesseract.js-core/tesseract-core-simd-lstm.wasm.js", "tesseract-core-simd-lstm.wasm.js"],
  ["node_modules/tesseract.js-core/tesseract-core-lstm.wasm.js", "tesseract-core-lstm.wasm.js"],
]
for (const [from, to] of copies) {
  const target = join(out, to)
  if (!existsSync(target)) copyFileSync(resolve(process.cwd(), from), target)
}

const models = ["eng", "ara"]
const base = process.env.MIZAN_TESSDATA_URL ?? "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"
for (const lang of models) {
  const target = join(out, `${lang}.traineddata.gz`)
  if (existsSync(target)) continue
  try {
    const response = await fetch(`${base}/${lang}.traineddata`)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const bytes = new Uint8Array(await response.arrayBuffer())
    const gz = new Response(new Blob([bytes]).stream().pipeThrough(new CompressionStream("gzip")))
    writeFileSync(target, new Uint8Array(await gz.arrayBuffer()))
    console.log(`ocr: fetched ${lang} model (${Math.round(bytes.length / 1024)} KB)`)
  } catch (error) {
    console.warn(`ocr: could not fetch the ${lang} model (${error.message}); receipt reading will be unavailable until it is`)
  }
}
