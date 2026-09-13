export interface ManualPage {
  slug: string
  order: number
  lang: string
  title: string
  screen: string | null
  summary: string
  body: string
}

const RAW = import.meta.glob("../../../docs/manual/*/*.md", { query: "?raw", import: "default", eager: true }) as Record<string, string>

/** Front matter is three fields on top of the file; a full YAML parser is not worth its weight. */
export function parseFrontMatter(text: string): { meta: Record<string, string>; body: string } {
  const match = /^---\n([\s\S]*?)\n---\n?/.exec(text)
  if (!match) return { meta: {}, body: text }
  const meta: Record<string, string> = {}
  for (const line of match[1].split("\n")) {
    const idx = line.indexOf(":")
    if (idx > 0) meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim().replace(/^"(.*)"$/, "$1")
  }
  return { meta, body: text.slice(match[0].length) }
}

function load(): ManualPage[] {
  return Object.entries(RAW).map(([path, text]) => {
    const [, lang, file] = /\/manual\/([a-z]{2})\/(\d+)-([a-z0-9-]+)\.md$/.exec(path)?.slice(0) ?? [path, "en", "0"]
    const order = Number(file)
    const slug = /\/(\d+)-([a-z0-9-]+)\.md$/.exec(path)?.[2] ?? path
    const { meta, body } = parseFrontMatter(text)
    return { slug, order, lang, title: meta.title ?? slug, screen: meta.screen ?? null, summary: meta.summary ?? "", body }
  })
}

const PAGES = load()

/** Pages for a language in reading order; Arabic falls back to English page by page. */
export function pagesFor(lang: string): ManualPage[] {
  const wanted = PAGES.filter((p) => p.lang === lang)
  const english = PAGES.filter((p) => p.lang === "en")
  return english
    .map((en) => wanted.find((p) => p.slug === en.slug) ?? en)
    .sort((a, b) => a.order - b.order)
}

export function pageFor(lang: string, slug: string): ManualPage | undefined {
  return pagesFor(lang).find((p) => p.slug === slug)
}

export function allPages(): ManualPage[] {
  return PAGES
}
