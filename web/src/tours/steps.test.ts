import { describe, expect, it } from "vitest"

import ar from "@/i18n/ar.json"
import en from "@/i18n/en.json"
import { TOURS } from "./steps"

function has(dict: unknown, key: string): boolean {
  return key.split(".").reduce<unknown>((node, part) => (node && typeof node === "object" ? (node as Record<string, unknown>)[part] : undefined), dict) !== undefined
}

describe("tour definitions", () => {
  for (const tour of Object.values(TOURS)) {
    it(`${tour.id} has strings in both languages and unique targets`, () => {
      for (const step of tour.steps) {
        expect(has(en, step.titleKey), step.titleKey).toBe(true)
        expect(has(ar, step.titleKey), step.titleKey).toBe(true)
        expect(has(en, step.bodyKey), step.bodyKey).toBe(true)
        expect(has(ar, step.bodyKey), step.bodyKey).toBe(true)
      }
      const targets = tour.steps.map((s) => s.target)
      expect(new Set(targets).size).toBe(targets.length)
    })
  }
})
