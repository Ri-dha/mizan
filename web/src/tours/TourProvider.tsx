import { EVENTS, Joyride, type EventData } from "react-joyride"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"

import { directionFor } from "@/i18n"
import { endTour, useActiveTour } from "./store"

/** One Joyride for the whole app, themed with the shadcn tokens and mirrored for Arabic. */
export function TourProvider() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const tour = useActiveTour()
  if (!tour) return null

  const rtl = directionFor(i18n.language) === "rtl"
  const steps = tour.steps.map((s, i) => ({
    target: s.target,
    title: t(s.titleKey),
    content: i === tour.steps.length - 1 ? `${t(s.bodyKey)}\n\n${t("tours.readMore")}` : t(s.bodyKey),
    placement: s.placement ?? "bottom",
    disableBeacon: true,
  }))

  function onEvent(data: EventData) {
    if (data.type === EVENTS.TOUR_END) {
      const finished = data.status === "finished"
      endTour()
      if (finished && data.action !== "skip") navigate(`/help/${tour!.helpSlug}`)
    }
  }

  return (
    <Joyride
      key={tour.id}
      steps={steps}
      run
      continuous
      onEvent={onEvent}
      locale={{ back: t("tours.back"), next: t("tours.next"), nextWithProgress: t("tours.nextWithProgress"), skip: t("tours.skip"), last: t("tours.last"), close: t("tours.close") }}
      options={{
        primaryColor: "var(--primary)",
        backgroundColor: "var(--popover)",
        textColor: "var(--foreground)",
        arrowColor: "var(--popover)",
        overlayColor: "rgba(0, 0, 0, 0.6)",
        zIndex: 60,
        spotlightPadding: 6,
        spotlightRadius: 5,
        showProgress: true,
        skipBeacon: true,
        closeButtonAction: "skip",
        targetWaitTimeout: 3000,
        buttons: ["skip", "back", "primary"],
      }}
      styles={{
        tooltip: { border: "1px solid var(--border)", borderRadius: 10, boxShadow: "0 10px 30px rgb(0 0 0 / 0.15)", direction: rtl ? "rtl" : "ltr", textAlign: rtl ? "right" : "left" },
        tooltipTitle: { fontWeight: 700 },
        buttonPrimary: { borderRadius: 8, color: "var(--primary-foreground)", fontWeight: 600 },
        buttonBack: { color: "var(--foreground)" },
        buttonSkip: { color: "var(--foreground)" },
      }}
    />
  )
}
