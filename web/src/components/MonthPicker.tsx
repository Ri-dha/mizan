import { ChevronLeft, ChevronRight } from "lucide-react"
import { useTranslation } from "react-i18next"

import { shiftMonth, formatMonthKey } from "@/app/month"
import { usePreferences } from "@/app/preferences"
import { formatHijri } from "@/domain/calendar/hijri"
import { Button } from "@/components/ui/button"

export function MonthPicker({ value, onChange }: { value: string; onChange: (key: string) => void }) {
  const { t, i18n } = useTranslation()
  const prefs = usePreferences()
  const hijri = prefs.showHijri ? formatHijri(`${value}-01`, i18n.language, { month: "long", year: "numeric" }) : ""
  return (
    <div className="flex items-center gap-2">
      <Button variant="neutral" size="icon" aria-label={t("month.previous")} onClick={() => onChange(shiftMonth(value, -1))}>
        <ChevronLeft className="rtl:-scale-x-100" />
      </Button>
      <span className="flex min-w-36 flex-col items-center font-heading"><span>{formatMonthKey(value, i18n.language)}</span>{hijri && <span className="text-xs font-normal opacity-70">{hijri}</span>}</span>
      <Button variant="neutral" size="icon" aria-label={t("month.next")} onClick={() => onChange(shiftMonth(value, 1))}>
        <ChevronRight className="rtl:-scale-x-100" />
      </Button>
    </div>
  )
}
