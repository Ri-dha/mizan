import { useTranslation } from "react-i18next"

import { usePreferences } from "./preferences"
import { formatDate } from "@/domain/calendar/hijri"

/** One place for the app's date rendering so the Hijri preference applies everywhere. */
export function useDateFormat() {
  const { i18n } = useTranslation()
  const prefs = usePreferences()
  return (iso: string, options?: Intl.DateTimeFormatOptions) => formatDate(iso, i18n.language, prefs.showHijri, options)
}
