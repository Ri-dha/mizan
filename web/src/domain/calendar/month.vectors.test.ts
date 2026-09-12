import { describe, expect, it } from "vitest"

import vectors from "../../../../shared/test-vectors/month-window.json"
import { monthKeyFor, monthWindow } from "./month"

interface Vector {
  name: string
  input: { key?: string; date?: string; startDay: number }
  expected: { from?: string; toExclusive?: string; key?: string }
}

describe("month-window vectors", () => {
  for (const vector of vectors as Vector[]) {
    it(vector.name, () => {
      if (vector.input.key) {
        const window = monthWindow(vector.input.key, vector.input.startDay)
        expect(window.from).toBe(vector.expected.from)
        expect(window.toExclusive).toBe(vector.expected.toExclusive)
      } else {
        expect(monthKeyFor(vector.input.date!, vector.input.startDay)).toBe(vector.expected.key)
      }
    })
  }
})
