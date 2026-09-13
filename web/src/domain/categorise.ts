import type { LedgerTransaction } from "@/db/schema"

export interface Suggestion {
  category: string
  bucketId: string | null
  confidence: "high" | "medium" | "low"
  reason: "history" | "keyword"
}

/** Categories and the words that point at them, in both languages; the user's own history beats these. */
const KEYWORDS: Record<string, string[]> = {
  Groceries: ["grocer", "supermarket", "market", "carrefour", "spar", "mart", "بقالة", "سوبرماركت", "ماركت", "خضار", "لحم"],
  Restaurants: ["restaurant", "cafe", "coffee", "burger", "pizza", "shawarma", "kebab", "مطعم", "كافيه", "قهوة", "شاورما", "كباب", "برجر"],
  Fuel: ["fuel", "petrol", "gas station", "benzin", "بنزين", "وقود", "محطة"],
  Transport: ["taxi", "careem", "uber", "bus", "parking", "تكسي", "كريم", "باص", "موقف", "نقل"],
  Electricity: ["electric", "kahraba", "كهرباء", "الكهرباء"],
  Generator: ["generator", "مولدة", "مولد", "امبير", "أمبير"],
  Water: ["water", "ماء", "مياه"],
  Internet: ["internet", "wifi", "earthlink", "fiber", "انترنت", "إنترنت", "ايرثلنك", "فايبر"],
  Phone: ["zain", "asiacell", "korek", "top-up", "topup", "recharge", "زين", "اسياسيل", "آسياسيل", "كورك", "رصيد", "شحن"],
  Pharmacy: ["pharmacy", "medicine", "صيدلية", "دواء", "ادوية", "أدوية"],
  Health: ["clinic", "doctor", "hospital", "lab", "dentist", "عيادة", "طبيب", "مستشفى", "مختبر", "اسنان", "أسنان"],
  Clothing: ["clothes", "clothing", "shoes", "fashion", "ملابس", "احذية", "أحذية", "قماش"],
  Education: ["school", "university", "tuition", "course", "books", "مدرسة", "جامعة", "دورة", "كتب", "قرطاسية"],
  Rent: ["rent", "landlord", "ايجار", "إيجار"],
  Gifts: ["gift", "wedding", "هدية", "عرس", "زواج", "خطوبة"],
  Entertainment: ["cinema", "game", "netflix", "spotify", "سينما", "لعبة", "ترفيه"],
  Home: ["furniture", "repair", "plumber", "electrician", "اثاث", "أثاث", "تصليح", "سباك", "كهربائي"],
}

/** Lowercase, no Arabic diacritics or tatweel, alef forms unified, one space between words. */
export function normalise(text: string): string {
  return text
    .toLowerCase()
    .replace(/[ً-ْـ]/g, "")
    .replace(/[إأآ]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي")
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim()
}

function mostCommon<T>(values: T[]): T | null {
  const counts = new Map<T, number>()
  for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1)
  let best: T | null = null
  let bestCount = 0
  for (const [v, count] of counts) if (count > bestCount) { best = v; bestCount = count }
  return best
}

/**
 * Phase 4 auto-categorisation without a model: what the household called this payee before,
 * then a bilingual keyword list. Never overrides a category the user already typed.
 */
export function suggestCategory(payee: string, note: string, history: LedgerTransaction[]): Suggestion | null {
  const key = normalise(payee)
  if (key) {
    const exact = history.filter((t) => t.type === "EXPENSE" && t.category && normalise(t.payee ?? "") === key)
    if (exact.length > 0) {
      return { category: mostCommon(exact.map((t) => t.category!))!, bucketId: mostCommon(exact.map((t) => t.bucketId)) ?? null, confidence: "high", reason: "history" }
    }
    const tokens = key.split(" ").filter((w) => w.length > 2)
    const partial = history.filter((t) => t.type === "EXPENSE" && t.category && tokens.some((w) => normalise(t.payee ?? "").split(" ").includes(w)))
    if (partial.length > 0) {
      return { category: mostCommon(partial.map((t) => t.category!))!, bucketId: mostCommon(partial.map((t) => t.bucketId)) ?? null, confidence: "medium", reason: "history" }
    }
  }
  const haystack = normalise(`${payee} ${note}`)
  if (!haystack) return null
  for (const [category, words] of Object.entries(KEYWORDS)) {
    if (words.some((w) => haystack.includes(normalise(w)))) {
      const sameCategory = history.filter((t) => t.type === "EXPENSE" && t.category === category)
      return { category, bucketId: mostCommon(sameCategory.map((t) => t.bucketId)) ?? null, confidence: "low", reason: "keyword" }
    }
  }
  return null
}
