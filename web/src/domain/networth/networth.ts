export type AssetClass = "CASH" | "METALS" | "RECEIVABLES"

export interface NetWorthInput {
  cash: number
  metals: number
  receivables: number
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
  composition: Share[]
}

const PERCENT_SCALE = 10

// One decimal place, half-up, in integer arithmetic so both implementations agree.
function percent(amount: number, assets: number): number {
  if (assets <= 0) return 0
  return Math.floor((amount * 100 * PERCENT_SCALE * 2 + assets) / (2 * assets)) / PERCENT_SCALE
}

/** BR-05: assets minus liabilities, with the composition by class (FR-NET-02). Everything in base minor units. */
export function netWorth(input: NetWorthInput): NetWorthResult {
  const totalAssets = input.cash + input.metals + input.receivables
  return {
    totalAssets,
    totalLiabilities: input.liabilities,
    netWorth: totalAssets - input.liabilities,
    composition: [
      { assetClass: "CASH", amount: input.cash, percent: percent(input.cash, totalAssets) },
      { assetClass: "METALS", amount: input.metals, percent: percent(input.metals, totalAssets) },
      { assetClass: "RECEIVABLES", amount: input.receivables, percent: percent(input.receivables, totalAssets) },
    ],
  }
}
