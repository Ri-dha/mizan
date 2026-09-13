/** FR-SET-04: the Umm al-Qura calendar alongside Gregorian; Intl does the arithmetic. */
export function formatHijri(iso: string, locale: string, options: Intl.DateTimeFormatOptions = { day: "numeric", month: "long", year: "numeric" }): string {
  const [y, m, d] = iso.split("-").map(Number)
  const tag = `${locale === "ar" ? "ar-SA" : "en-GB"}-u-ca-islamic-umalqura-nu-latn`
  try {
    return new Intl.DateTimeFormat(tag, { ...options, timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, d)))
  } catch {
    return ""
  }
}

export function formatGregorian(iso: string, locale: string, options: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" }): string {
  const [y, m, d] = iso.split("-").map(Number)
  return new Intl.DateTimeFormat(locale === "ar" ? "ar-IQ" : "en-GB", { ...options, timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, d)))
}

/** "13 Sept 2026 · 1 Rabiʻ II 1448" when Hijri is on, the Gregorian date alone otherwise. */
export function formatDate(iso: string, locale: string, withHijri: boolean, options?: Intl.DateTimeFormatOptions): string {
  const gregorian = formatGregorian(iso, locale, options)
  if (!withHijri) return gregorian
  const hijri = formatHijri(iso, locale)
  return hijri ? `${gregorian} · ${hijri}` : gregorian
}
