import { useTranslation } from "react-i18next"

import { Button } from "@/components/ui/button"

export function LanguageToggle() {
  const { i18n } = useTranslation()
  const next = i18n.language.startsWith("ar") ? "en" : "ar"
  return (
    <Button variant="neutral" size="sm" onClick={() => void i18n.changeLanguage(next)}>
      {next === "ar" ? "العربية" : "English"}
    </Button>
  )
}
