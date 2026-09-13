// Builds the static manual at dist/docs from docs/manual, the same Markdown the app bundles.
// Fails when an English page has no Arabic counterpart, so the manual cannot drift by language.
import { readdirSync, readFileSync, mkdirSync, writeFileSync } from "node:fs"
import { join, resolve } from "node:path"
import { marked } from "marked"

const source = resolve(process.cwd(), "../docs/manual")
const out = resolve(process.cwd(), "dist/docs")
const languages = { en: { dir: "ltr", name: "English", index: "Mizan user manual", other: "العربية" }, ar: { dir: "rtl", name: "العربية", index: "دليل مستخدم ميزان", other: "English" } }

function frontMatter(text) {
  const match = /^---\n([\s\S]*?)\n---\n?/.exec(text)
  const meta = {}
  if (!match) return { meta, body: text }
  for (const line of match[1].split("\n")) {
    const i = line.indexOf(":")
    if (i > 0) meta[line.slice(0, i).trim()] = line.slice(i + 1).trim().replace(/^"(.*)"$/, "$1")
  }
  return { meta, body: text.slice(match[0].length) }
}

function pages(lang) {
  return readdirSync(join(source, lang)).filter((f) => f.endsWith(".md")).sort().map((file) => {
    const { meta, body } = frontMatter(readFileSync(join(source, lang, file), "utf8"))
    return { slug: file.replace(/^\d+-/, "").replace(/\.md$/, ""), title: meta.title ?? file, summary: meta.summary ?? "", html: marked.parse(body) }
  })
}

const css = `
:root{--bg:#eef2fa;--fg:#000;--main:#88aaee;--border:#000;--card:#fff}
body{margin:0;font-family:system-ui,sans-serif;background:var(--bg);color:var(--fg);line-height:1.6}
header{border-bottom:4px solid var(--border);background:var(--card);padding:12px 24px;display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
header a{font-weight:700;color:var(--fg)}
main{max-width:820px;margin:0 auto;padding:24px}
nav ul{list-style:none;padding:0;display:grid;gap:12px;grid-template-columns:repeat(auto-fill,minmax(260px,1fr))}
nav li{border:2px solid var(--border);border-radius:5px;background:var(--card);box-shadow:4px 4px 0 0 var(--border);padding:12px}
[dir=rtl] nav li{box-shadow:-4px 4px 0 0 var(--border)}
nav li a{font-weight:700;color:var(--fg);text-decoration:none}
article h1{font-size:2rem}article h2{margin-top:1.5em}
article table{border-collapse:collapse;width:100%}article th,article td{border:2px solid var(--border);padding:4px 8px;text-align:start}
article code{background:var(--card);padding:0 4px;border-radius:4px}
.pager{display:flex;justify-content:space-between;margin-top:32px;gap:12px}
@media print{header,.pager{display:none}main{max-width:none;padding:0}body{background:#fff}}
`
const shell = (lang, title, body) => `<!doctype html><html lang="${lang}" dir="${languages[lang].dir}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>${css}</style></head><body><header><a href="/docs/${lang}/">${languages[lang].index}</a><span><a href="/">Mizan</a> · <a href="/docs/${lang === "en" ? "ar" : "en"}/">${languages[lang].other}</a> · <a href="#" onclick="print();return false">PDF</a></span></header><main>${body}</main></body></html>`

const en = pages("en")
const ar = pages("ar")
const missing = en.filter((p) => !ar.some((a) => a.slug === p.slug)).map((p) => p.slug)
if (missing.length) {
  console.error(`Manual pages without an Arabic version: ${missing.join(", ")}`)
  process.exit(1)
}

for (const [lang, list] of [["en", en], ["ar", ar]]) {
  mkdirSync(join(out, lang), { recursive: true })
  const index = `<h1>${languages[lang].index}</h1><nav><ul>${list.map((p) => `<li><a href="/docs/${lang}/${p.slug}.html">${p.title}</a><br><small>${p.summary}</small></li>`).join("")}</ul></nav>`
  writeFileSync(join(out, lang, "index.html"), shell(lang, languages[lang].index, index))
  list.forEach((p, i) => {
    const prev = list[i - 1], next = list[i + 1]
    const pager = `<div class="pager"><span>${prev ? `<a href="/docs/${lang}/${prev.slug}.html">← ${prev.title}</a>` : ""}</span><span>${next ? `<a href="/docs/${lang}/${next.slug}.html">${next.title} →</a>` : ""}</span></div>`
    writeFileSync(join(out, lang, `${p.slug}.html`), shell(lang, p.title, `<article>${p.html}</article>${pager}`))
  })
}
writeFileSync(join(out, "index.html"), `<!doctype html><meta http-equiv="refresh" content="0; url=/docs/en/">`)
console.log(`docs: ${en.length} pages × 2 languages → ${out}`)
