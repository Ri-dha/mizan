import { useLiveQuery } from "dexie-react-hooks"

import { liveDisposalLots, liveDisposals, liveLots, lotStates, portfolio, valueLots, type LotValuation, type PortfolioLine } from "@/db/metals"
import type { MarketOverride, MarketSetting, MetalDisposal, MetalDisposalLot, MetalLot } from "@/db/schema"
import { cachedQuotes, liveOverrides, liveSetting, resolvePrices, type Quote, type ResolvedPrices } from "@/market/store"

export interface MetalsView {
  lots: MetalLot[]
  valued: LotValuation[]
  lines: PortfolioLine[]
  disposals: MetalDisposal[]
  disposalLots: MetalDisposalLot[]
  quotes: Quote[]
  overrides: MarketOverride[]
  setting: MarketSetting | undefined
  prices: ResolvedPrices
}

const EMPTY_PRICES: ResolvedPrices = {
  usdIqdMicros: 0, rateKind: "PARALLEL", rateSource: { kind: "none", label: "none", at: null, stale: true },
  spot: {
    GOLD: { spotUsdPerOzMicros: 0, perGram24kOverrideMicros: null, source: { kind: "none", label: "none", at: null, stale: true }, bidFromFeed: false },
    SILVER: { spotUsdPerOzMicros: 0, perGram24kOverrideMicros: null, source: { kind: "none", label: "none", at: null, stale: true }, bidFromFeed: false },
  },
}

const EMPTY: MetalsView = { lots: [], valued: [], lines: [], disposals: [], disposalLots: [], quotes: [], overrides: [], setting: undefined, prices: EMPTY_PRICES }

export function useMetals(): MetalsView {
  return useLiveQuery(async () => {
    const [lots, disposals, disposalLots, quotes, overrides, setting] = await Promise.all([
      liveLots(), liveDisposals(), liveDisposalLots(), cachedQuotes(), liveOverrides(), liveSetting(),
    ])
    const prices = resolvePrices(quotes, overrides, setting)
    const valued = valueLots(lotStates(lots, disposalLots), prices, setting)
    return { lots, valued, lines: portfolio(valued), disposals, disposalLots, quotes, overrides, setting, prices }
  }, [], EMPTY)
}
