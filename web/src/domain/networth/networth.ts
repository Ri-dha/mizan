export type AssetClass = "CASH" | "METALS" | "RECEIVABLES" | "VEHICLES" | "PROPERTY" | "OTHER_ASSETS"

/** `illiquid` is the part of vehicles, property and other assets the household marked illiquid. */
export interface NetWorthInput {
  cash: number
  metals: number
  receivables: number
  vehicles: number
  property: number
  otherAssets: number
  illiquid: number
  liabilities: number
}

export interface Share {
  assetClass: AssetClass
  amount: number
  percent: number
}

export interface NetWorthResult {
  totalAssets: number
  totalLiabilities: number
  netWorth: number
  liquidAssets: number
  illiquidAssets: number
  composition: Share[]
}

const PERCENT_SCALE = 10

// One decimal place, half-up, in integer arithmetic so both implementations agree.
function percent(amount: number, assets: number): number {
  if (assets <= 0) return 0
  return Math.floor((amount * 100 * PERCENT_SCALE * 2 + assets) / (2 * assets)) / PERCENT_SCALE
}

/** BR-05: assets minus liabilities, with the composition by class (FR-NET-02) and the liquid split (FR-NET-06). */
export function netWorth(input: NetWorthInput): NetWorthResult {
  const totalAssets = input.cash + input.metals + input.receivables + input.vehicles + input.property + input.otherAssets
  const share = (assetClass: AssetClass, amount: number): Share => ({ assetClass, amount, percent: percent(amount, totalAssets) })
  return {
    totalAssets,
    totalLiabilities: input.liabilities,
    netWorth: totalAssets - input.liabilities,
    liquidAssets: totalAssets - input.illiquid,
    illiquidAssets: input.illiquid,
    composition: [
      share("CASH", input.cash),
      share("METALS", input.metals),
      share("RECEIVABLES", input.receivables),
      share("VEHICLES", input.vehicles),
      share("PROPERTY", input.property),
      share("OTHER_ASSETS", input.otherAssets),
    ],
  }
}
