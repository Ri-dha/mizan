import { preferences } from "@/app/preferences"

const ZERO_DECIMAL_CURRENCIES: ReadonlySet<string> = new Set(["IQD"])

/** IQD shows whole dinars with grouping (FR-SET-03); other currencies use their own minor unit. */
export function formatMoney(minorUnits: number, currency: string, locale: string): string {
  const zeroDecimal = ZERO_DECIMAL_CURRENCIES.has(currency)
  const amount = zeroDecimal ? minorUnits : minorUnits / 100
  const eastern = preferences().digitStyle === "eastern"
  return new Intl.NumberFormat(locale === "ar" ? (eastern ? "ar-IQ-u-nu-arab" : "ar-IQ-u-nu-latn") : "en-IQ", {
    style: "currency",
    currency,
    currencyDisplay: "code",
    maximumFractionDigits: zeroDecimal ? 0 : 2,
    minimumFractionDigits: zeroDecimal ? 0 : 2,
  }).format(amount)
}

export function toMinorUnits(input: string, currency: string): number {
  const cleaned = input.replace(/[^\d.-]/g, "")
  const value = Number(cleaned)
  if (Number.isNaN(value)) return 0
  return ZERO_DECIMAL_CURRENCIES.has(currency) ? Math.round(value) : Math.round(value * 100)
}

export function fromMinorUnits(minorUnits: number, currency: string): string {
  return ZERO_DECIMAL_CURRENCIES.has(currency) ? String(minorUnits) : (minorUnits / 100).toFixed(2)
}
