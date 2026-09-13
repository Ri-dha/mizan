import { describe, expect, it } from "vitest"

import { allPages, pagesFor, parseFrontMatter } from "./manual"

const ROUTES = ["/", "/plan", "/income", "/bills", "/transactions", "/debts", "/goals", "/metals", "/networth", "/report", "/accounts", "/sync", "/settings", "/help"]

describe("manual", () => {
  it("parses front matter", () => {
    const { meta, body } = parseFrontMatter('---\ntitle: "Hello"\nscreen: /plan\n---\n# Body')
    expect(meta).toEqual({ title: "Hello", screen: "/plan" })
    expect(body).toBe("# Body")
  })

  it("every page has a title, a summary and a known screen", () => {
    expect(allPages().length).toBeGreaterThan(0)
    for (const page of allPages()) {
      expect(page.title, page.slug).not.toBe(page.slug)
      expect(page.summary, page.slug).not.toBe("")
      if (page.screen) expect(ROUTES, `${page.lang}/${page.slug}`).toContain(page.screen)
    }
  })

  it("Arabic covers every English page", () => {
    const en = pagesFor("en").map((p) => p.slug)
    const ar = pagesFor("ar")
    expect(ar.filter((p) => p.lang === "ar").map((p) => p.slug).sort()).toEqual([...en].sort())
  })
})
