import { api, unwrap } from "@/api/client"
import { META_KEYS, readMeta, writeMeta } from "@/db/meta"
import { db, type MarketInstrument, type MarketOverride, type MarketSetting, type Metal, type RateKind } from "@/db/schema"
import { writeFields } from "@/db/write"
import { todayIso } from "@/domain/calendar/month"

export interface Quote {
  instrument: "XAU" | "XAG" | "USDIQD_OFFICIAL" | "USDIQD_PARALLEL"
  priceMicros: number
  source: string
  quotedAt: string
  fetchedAt: string
  stale: boolean
}

const QUOTES_KEY = "market.quotes"
const STALE_AFTER_MS = 24 * 60 * 60 * 1000

/** Pulled with every sync; served from the local copy so valuation works offline (FR-MKT-05). */
export async function refreshQuotes(): Promise<void> {
  const quotes = await unwrap(api.GET("/api/v1/market/quotes"))
  await writeMeta(QUOTES_KEY, quotes)
}

export async function cachedQuotes(): Promise<Quote[]> {
  return (await readMeta<Quote[]>(QUOTES_KEY)) ?? []
}

export function isStale(quote: Quote, now = Date.now()): boolean {
  return quote.stale || now - Date.parse(quote.fetchedAt) > STALE_AFTER_MS
}

export const DEFAULT_SETTING: Omit<MarketSetting, keyof import("@/db/schema").Syncable | "visibility"> = {
  rateKind: "PARALLEL",
  goldPremiumBasisPoints: 0,
  silverPremiumBasisPoints: 0,
  goldMethod: "FIFO",
  silverMethod: "FIFO",
  valuationBasis: "MARKET",
  goldBuybackBasisPoints: 0,
  silverBuybackBasisPoints: 0,
}

export async function liveSetting(): Promise<MarketSetting | undefined> {
  return (await db.marketSettings.filter((s) => s.deletedAt === null).toArray())[0]
}

export async function saveSetting(changes: Partial<typeof DEFAULT_SETTING>) {
  const existing = await liveSetting()
  if (existing) await writeFields<MarketSetting>("market_setting", existing.id, changes)
  else await writeFields<MarketSetting>("market_setting", crypto.randomUUID(), { visibility: "SHARED", ...DEFAULT_SETTING, ...changes })
}

export function liveOverrides() {
  return db.marketOverrides.filter((o) => o.deletedAt === null).toArray()
}

/** FR-MTL-09: the latest user-entered price for an instrument, if any. */
export function activeOverride(overrides: MarketOverride[], instrument: MarketInstrument): MarketOverride | undefined {
  return overrides
    .filter((o) => o.instrument === instrument && o.effectiveFrom <= todayIso())
    .sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? 1 : -1))[0]
}

export async function setOverride(instrument: MarketInstrument, priceMicros: number, note: string | null) {
  await writeFields<MarketOverride>("market_override", crypto.randomUUID(), {
    visibility: "SHARED", instrument, priceMicros, effectiveFrom: todayIso(), note,
  })
}

export interface PriceSource {
  kind: "override" | "feed" | "none"
  label: string
  at: string | null
  stale: boolean
}

export interface ResolvedPrices {
  usdIqdMicros: number
  rateKind: RateKind
  rateSource: PriceSource
  spot: Record<Metal, { spotUsdPerOzMicros: number; perGram24kOverrideMicros: number | null; source: PriceSource }>
}

/**
 * What valuation actually uses (BR-10): a user override beats the feed, the chosen dollar
 * rate governs conversion, and every number remembers where it came from.
 */
export function resolvePrices(quotes: Quote[], overrides: MarketOverride[], setting: MarketSetting | undefined): ResolvedPrices {
  const rateKind = setting?.rateKind ?? DEFAULT_SETTING.rateKind
  const rateQuote = quotes.find((q) => q.instrument === (rateKind === "OFFICIAL" ? "USDIQD_OFFICIAL" : "USDIQD_PARALLEL"))
  const rateOverride = activeOverride(overrides, "USDIQD")
  const usdIqdMicros = rateOverride?.priceMicros ?? rateQuote?.priceMicros ?? 0

  const metal = (m: Metal) => {
    const quote = quotes.find((q) => q.instrument === (m === "GOLD" ? "XAU" : "XAG"))
    const override = activeOverride(overrides, m === "GOLD" ? "XAU" : "XAG")
    return {
      spotUsdPerOzMicros: quote?.priceMicros ?? 0,
      perGram24kOverrideMicros: override?.priceMicros ?? null,
      source: describe(override, quote),
    }
  }
  return { usdIqdMicros, rateKind, rateSource: describe(rateOverride, rateQuote), spot: { GOLD: metal("GOLD"), SILVER: metal("SILVER") } }
}

function describe(override: MarketOverride | undefined, quote: Quote | undefined): PriceSource {
  if (override) return { kind: "override", label: "override", at: override.effectiveFrom, stale: false }
  if (quote) return { kind: "feed", label: quote.source, at: quote.fetchedAt, stale: isStale(quote) }
  return { kind: "none", label: "none", at: null, stale: true }
}

export { META_KEYS }
