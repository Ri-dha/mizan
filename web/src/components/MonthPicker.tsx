import { ChevronLeft, ChevronRight } from "lucide-react"
import { useTranslation } from "react-i18next"

import { shiftMonth, formatMonthKey } from "@/app/month"
import { Button } from "@/components/ui/button"

export function MonthPicker({ value, onChange }: { value: string; onChange: (key: string) => void }) {
  const { t, i18n } = useTranslation()
  return (
    <div className="flex items-center gap-2">
      <Button variant="neutral" size="icon" aria-label={t("month.previous")} onClick={() => onChange(shiftMonth(value, -1))}>
        <ChevronLeft className="rtl:-scale-x-100" />
      </Button>
      <span className="min-w-36 text-center font-heading">{formatMonthKey(value, i18n.language)}</span>
      <Button variant="neutral" size="icon" aria-label={t("month.next")} onClick={() => onChange(shiftMonth(value, 1))}>
        <ChevronRight className="rtl:-scale-x-100" />
      </Button>
    </div>
  )
}
