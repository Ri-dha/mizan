import { describe, expect, it } from "vitest"

import { extractDate, extractReceipt, westernDigits } from "./receipt"

describe("receipt reading", () => {
  it("finds the total on the total line, the date and the merchant", () => {
    const text = "Al Hamra Market\nBaghdad\n05/09/2026 14:21\nRice 5kg 12,000\nOil 8,500\nTOTAL 20,500\nThank you"
    expect(extractReceipt(text)).toEqual({ total: 20500, date: "2026-09-05", merchant: "Al Hamra Market" })
  })

  it("reads Arabic receipts with Eastern digits", () => {
    const text = "مطعم النخيل\nالتاريخ ٢٠٢٦-٠٩-١٣\nشاورما ٦٠٠٠\nعصير ٣٠٠٠\nالمجموع ٩٠٠٠"
    expect(westernDigits("٩٠٠٠")).toBe("9000")
    expect(extractReceipt(text)).toEqual({ total: 9000, date: "2026-09-13", merchant: "مطعم النخيل" })
  })

  it("falls back to the largest number when no total line exists", () => {
    expect(extractReceipt("Zain\ntop up 10000\nfee 250").total).toBe(10000)
    expect(extractDate("no date here")).toBeNull()
  })
})
