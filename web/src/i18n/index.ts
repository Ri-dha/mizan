import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import { initReactI18next } from "react-i18next"

import ar from "./ar.json"
import en from "./en.json"

export const SUPPORTED_LOCALES = ["en", "ar"] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

const RTL_LOCALES: ReadonlySet<string> = new Set(["ar"])

export function directionFor(locale: string): "rtl" | "ltr" {
  return RTL_LOCALES.has(locale) ? "rtl" : "ltr"
}

export function applyDocumentLocale(locale: string) {
  document.documentElement.lang = locale
  document.documentElement.dir = directionFor(locale)
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { en: { translation: en }, ar: { translation: ar } },
    supportedLngs: [...SUPPORTED_LOCALES],
    fallbackLng: "en",
    interpolation: { escapeValue: false },
    detection: { order: ["localStorage", "navigator"], caches: ["localStorage"] },
  })

i18n.on("languageChanged", applyDocumentLocale)
applyDocumentLocale(i18n.language)

export default i18n
