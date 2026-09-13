/**
 * FR-NET-05: why net worth moved between two readings. Price movement is the change in
 * holdings not explained by buying or selling; debt repayment is the fall in what is owed;
 * saving is whatever remains (cash and money owed to you). The four sum to the total by
 * construction, so the breakdown always reconciles.
 */
export interface Reading {
  cash: number
  metals: number
  receivables: number
  otherAssets: number
  liabilities: number
}

export interface PeriodFlows {
  /** Cost of gold, silver and other assets bought in the period, in base units. */
  purchases: number
  /** Cost basis of holdings sold in the period. */
  disposals: number
}

export interface Attribution {
  total: number
  saving: number
  priceMovement: number
  debtRepayment: number
  newPurchases: number
}

const netWorthOf = (r: Reading) => r.cash + r.metals + r.receivables + r.otherAssets - r.liabilities

export function attributeChange(before: Reading, after: Reading, flows: PeriodFlows): Attribution {
  const total = netWorthOf(after) - netWorthOf(before)
  const holdingsChange = after.metals + after.otherAssets - before.metals - before.otherAssets
  const newPurchases = flows.purchases - flows.disposals
  const priceMovement = holdingsChange - newPurchases
  const debtRepayment = before.liabilities - after.liabilities
  const saving = total - priceMovement - debtRepayment - newPurchases
  return { total, saving, priceMovement, debtRepayment, newPurchases }
}
