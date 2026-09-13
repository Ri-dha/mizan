import type { Worker } from "tesseract.js"

export interface ReceiptReading {
  total: number | null
  date: string | null
  merchant: string | null
  text: string
}

const ASSETS = "/ocr"
const LANGS = "eng+ara"
const TOTAL_WORDS = /total|amount due|grand|balance due|net|المجموع|الاجمالي|الإجمالي|المبلغ|الصافي|الكلي/i
const ARABIC_DIGITS: Record<string, string> = { "٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4", "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9" }

let worker: Promise<Worker> | null = null

/** Everything runs on the device from files under /ocr, so a receipt is never sent anywhere (BR-16). */
async function getWorker(): Promise<Worker> {
  if (!worker) {
    worker = import("tesseract.js").then(({ createWorker }) =>
      createWorker(LANGS, 1, { workerPath: `${ASSETS}/worker.min.js`, corePath: ASSETS, langPath: ASSETS, gzip: true, workerBlobURL: false }),
    )
  }
  return worker
}

export async function readReceipt(image: Blob): Promise<ReceiptReading> {
  const w = await getWorker()
  const { data } = await w.recognize(image)
  return { ...extractReceipt(data.text), text: data.text }
}

export function westernDigits(text: string): string {
  return text.replace(/[٠-٩]/g, (d) => ARABIC_DIGITS[d] ?? d)
}

function numbersIn(line: string): number[] {
  return [...westernDigits(line).matchAll(/\d{1,3}(?:[,.\s]\d{3})+|\d+(?:[.,]\d{1,2})?/g)]
    .map((m) => Number(m[0].replace(/[,\s]/g, (c) => (c === "," && /,\d{2}$/.test(m[0]) ? "." : ""))))
    .filter((n) => Number.isFinite(n) && n > 0)
}

export function extractDate(text: string): string | null {
  const t = westernDigits(text)
  let m = /(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/.exec(t)
  if (m) return iso(Number(m[1]), Number(m[2]), Number(m[3]))
  m = /(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})/.exec(t)
  if (m) {
    const year = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3])
    return iso(year, Number(m[2]), Number(m[1]))
  }
  return null
}

function iso(y: number, m: number, d: number): string | null {
  if (m < 1 || m > 12 || d < 1 || d > 31 || y < 2000 || y > 2100) return null
  return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`
}

/** Heuristics over OCR text: the number on the "total" line, else the largest number; a date; the first wordy line. */
export function extractReceipt(text: string): Omit<ReceiptReading, "text"> {
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  let total: number | null = null
  for (let i = 0; i < lines.length; i++) {
    if (!TOTAL_WORDS.test(lines[i])) continue
    const candidates = [...numbersIn(lines[i]), ...numbersIn(lines[i + 1] ?? "")]
    if (candidates.length > 0) {
      total = Math.max(...candidates)
      break
    }
  }
  if (total === null) {
    const all = lines.flatMap(numbersIn).filter((n) => n < 1_000_000_000)
    total = all.length > 0 ? Math.max(...all) : null
  }
  const merchant = lines.find((l) => /\p{L}{3,}/u.test(l) && !/\d{4}/.test(l) && l.length <= 40) ?? null
  return { total, date: extractDate(text), merchant }
}
