import { describe, expect, it } from "vitest"

import { formatDate, formatHijri } from "./hijri"

describe("hijri dates", () => {
  it("renders a known date in the Umm al-Qura calendar", () => {
    expect(formatHijri("2024-03-11", "en")).toContain("1445")
    expect(formatHijri("2024-03-11", "en")).toContain("Ramadan")
  })

  it("appends the Hijri date only when asked", () => {
    expect(formatDate("2024-03-11", "en", false)).toBe("11 Mar 2024")
    expect(formatDate("2024-03-11", "en", true)).toMatch(/^11 Mar 2024 · .*1445/)
  })
})
