import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import Markdown from "react-markdown"
import { Link, useParams } from "react-router"
import remarkGfm from "remark-gfm"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { pageFor, pagesFor } from "@/help/manual"

/** The manual, bundled with the app so it reads offline; the same Markdown builds /docs. */
export function HelpPage() {
  const { t, i18n } = useTranslation()
  const { slug } = useParams()
  const lang = i18n.language.startsWith("ar") ? "ar" : "en"
  const pages = useMemo(() => pagesFor(lang), [lang])
  const [query, setQuery] = useState("")
  const page = slug ? pageFor(lang, slug) : undefined

  if (page) {
    return (
      <article className="flex max-w-3xl flex-col gap-4">
        <Link to="/help" className="text-sm underline">{t("help.backToIndex")}</Link>
        <h1 className="text-3xl">{page.title}</h1>
        {page.lang !== lang && <p className="text-sm opacity-70">{t("help.englishOnly")}</p>}
        <div className="manual">
          <Markdown remarkPlugins={[remarkGfm]}>{page.body}</Markdown>
        </div>
        {page.screen && <Link to={page.screen} className="underline">{t("help.openScreen")}</Link>}
      </article>
    )
  }

  const needle = query.trim().toLowerCase()
  const shown = pages.filter((p) => !needle || p.title.toLowerCase().includes(needle) || p.summary.toLowerCase().includes(needle) || p.body.toLowerCase().includes(needle))

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl">{t("help.title")}</h1>
      <Input placeholder={t("help.search")} value={query} onChange={(e) => setQuery(e.target.value)} />
      <div className="grid gap-3 sm:grid-cols-2">
        {shown.map((p) => (
          <Link key={p.slug} to={`/help/${p.slug}`}>
            <Card className="h-full">
              <CardHeader>
                <CardTitle>{p.title}</CardTitle>
                <CardDescription>{p.summary}</CardDescription>
              </CardHeader>
              <CardContent className="text-xs opacity-70">{p.lang !== lang ? t("help.englishOnly") : ""}</CardContent>
            </Card>
          </Link>
        ))}
        {shown.length === 0 && <p className="opacity-70">{t("help.noMatch")}</p>}
      </div>
    </div>
  )
}
