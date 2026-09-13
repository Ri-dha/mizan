import { CircleHelp } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { TOURS } from "@/tours/steps"
import { startTour } from "@/tours/store"

/** The ? in a screen header: replay that screen's tour or open its manual page. */
export function HelpButton({ tour }: { tour: keyof typeof TOURS }) {
  const { t } = useTranslation()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="neutral" size="icon" aria-label={t("help.title")}><CircleHelp /></Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => startTour(tour)}>{t("tours.showMeAround")}</DropdownMenuItem>
        <DropdownMenuItem asChild><Link to={`/help/${TOURS[tour].helpSlug}`}>{t("help.openGuide")}</Link></DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
