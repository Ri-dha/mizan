import { Megaphone, X } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { liveAnnouncements, type Announcement } from "@/api/admin"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const DISMISSED_KEY = "mizan.dismissedAnnouncements"
const REFRESH_MS = 10 * 60 * 1000

function dismissed(): string[] {
  try {
    return JSON.parse(localStorage.getItem(DISMISSED_KEY) ?? "[]") as string[]
  } catch {
    return []
  }
}

/** Notices from the operators (ADR admin-1), in the reader's language; each can be closed once read. */
export function AnnouncementBanner() {
  const { t, i18n } = useTranslation()
  const [items, setItems] = useState<Announcement[]>([])
  const [hidden, setHidden] = useState<string[]>(dismissed)

  useEffect(() => {
    const load = () => {
      if (navigator.onLine) liveAnnouncements().then(setItems).catch(() => undefined)
    }
    load()
    const timer = setInterval(load, REFRESH_MS)
    return () => clearInterval(timer)
  }, [])

  function close(id: string) {
    const next = [...hidden, id]
    setHidden(next)
    try {
      localStorage.setItem(DISMISSED_KEY, JSON.stringify(next.slice(-50)))
    } catch {
      // Without storage it comes back next visit, which is acceptable for a notice.
    }
  }

  const shown = items.filter((a) => a.id && !hidden.includes(a.id))
  if (shown.length === 0) return null
  return (
    <div className="mb-4 flex flex-col gap-2 print:hidden">
      {shown.map((a) => (
        <Alert key={a.id} variant={a.level === "WARNING" ? "destructive" : "default"} className={cn("pe-12", a.level !== "WARNING" && "border-primary/40")}>
          <Megaphone />
          <AlertDescription className="text-foreground">{i18n.language === "ar" ? a.messageAr : a.messageEn}</AlertDescription>
          <Button size="icon" variant="ghost" className="absolute top-2 end-2 size-7" aria-label={t("admin.announcements.dismiss")} onClick={() => close(a.id!)}>
            <X />
          </Button>
        </Alert>
      ))}
    </div>
  )
}
