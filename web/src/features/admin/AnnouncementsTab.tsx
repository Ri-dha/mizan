import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { fetchAnnouncements, publishAnnouncement, retractAnnouncement, type Announcement } from "@/api/admin"
import { Field } from "@/components/Field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useAdminAction, useWhen } from "./adminAction"

export function AnnouncementsTab() {
  const { t } = useTranslation()
  const run = useAdminAction()
  const when = useWhen()
  const [items, setItems] = useState<Announcement[]>([])
  const [en, setEn] = useState("")
  const [ar, setAr] = useState("")
  const [level, setLevel] = useState<"INFO" | "WARNING">("INFO")
  const [endsAt, setEndsAt] = useState("")

  const reload = async () => setItems(await fetchAnnouncements())
  useEffect(() => {
    fetchAnnouncements().then(setItems).catch(() => setItems([]))
  }, [])

  async function publish() {
    const ok = await run(async () => {
      await publishAnnouncement({ messageEn: en, messageAr: ar, level, endsAt: endsAt ? new Date(endsAt).toISOString() : undefined })
      await reload()
    }, t("admin.announcements.published"))
    if (ok) { setEn(""); setAr(""); setEndsAt("") }
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.announcements.title")}</CardTitle>
          <CardDescription>{t("admin.announcements.body")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <Field id="ann-en" label={t("admin.announcements.english")}>
            <Input id="ann-en" dir="ltr" maxLength={500} value={en} onChange={(e) => setEn(e.target.value)} />
          </Field>
          <Field id="ann-ar" label={t("admin.announcements.arabic")}>
            <Input id="ann-ar" dir="rtl" maxLength={500} value={ar} onChange={(e) => setAr(e.target.value)} />
          </Field>
          <div className="flex flex-wrap items-end gap-3">
            <Field id="ann-level" label={t("admin.announcements.level")}>
              <Select value={level} onValueChange={(v) => setLevel(v as "INFO" | "WARNING")}>
                <SelectTrigger id="ann-level" className="w-40"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INFO">{t("admin.announcements.info")}</SelectItem>
                  <SelectItem value="WARNING">{t("admin.announcements.warning")}</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field id="ann-ends" label={t("admin.announcements.endsAt")}>
              <Input id="ann-ends" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} />
            </Field>
            <Button disabled={!en.trim() || !ar.trim()} onClick={() => void publish()}>{t("admin.announcements.publish")}</Button>
          </div>
        </CardContent>
      </Card>

      {items.map((a) => (
        <Card key={a.id}>
          <CardHeader>
            <CardTitle className="flex flex-wrap items-center gap-2 text-base">
              {a.live ? <Badge>{t("admin.announcements.live")}</Badge> : <Badge variant="secondary">{a.retractedAt ? t("admin.announcements.retracted") : t("admin.announcements.notLive")}</Badge>}
              {a.level === "WARNING" && <Badge variant="destructive">{t("admin.announcements.warning")}</Badge>}
            </CardTitle>
            <CardDescription>{when(a.createdAt)}{a.endsAt ? ` → ${when(a.endsAt)}` : ""}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <p dir="ltr" className="text-start">{a.messageEn}</p>
            <p dir="rtl" className="text-start">{a.messageAr}</p>
            {!a.retractedAt && (
              <Button size="sm" variant="outline" className="self-start" onClick={() => void run(async () => { await retractAnnouncement(a.id ?? ""); await reload() }, t("admin.announcements.retractDone"))}>
                {t("admin.announcements.retract")}
              </Button>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
