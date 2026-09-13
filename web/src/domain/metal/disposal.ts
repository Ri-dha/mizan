export type DisposalMethod = "FIFO" | "SPECIFIC" | "WEIGHTED_AVERAGE"

export interface PlannerLot {
  id: string
  purchaseDate: string
  weightMg: number
  remainingMg: number
  metalCost: number
  makingCharge: number
  fees: number
}

export interface Allocation {
  lotId: string
  weightMg: number
  metalCost: number
  makingCharge: number
  fees: number
}

export interface DisposalPlan {
  allocations: Allocation[]
  costMetal: number
  costMaking: number
  costFees: number
  costBasis: number
  realisedGain: number
  realisedGainExcludingMaking: number
}

export class InsufficientWeightError extends Error {
  constructor() {
    super("Not enough open metal to sell that weight")
  }
}

function share(amount: number, part: number, whole: number): number {
  return Math.floor((amount * part + whole / 2) / whole)
}

/**
 * Decides which lots a sale draws from and what cost basis leaves with the metal (BR-11).
 * Making charges and fees travel as their own components so the gain can be shown with and
 * without them (BR-12). Weighted average draws metal oldest-first but prices every gram at
 * the average cost of everything open.
 */
export function planDisposal(lots: PlannerLot[], weightMg: number, method: DisposalMethod, specificLotIds: string[], proceeds: number): DisposalPlan {
  const openTotal = lots.reduce((sum, lot) => sum + lot.remainingMg, 0)
  if (weightMg <= 0 || weightMg > openTotal) throw new InsufficientWeightError()

  const order = method === "SPECIFIC"
    ? specificLotIds.map((id) => lots.find((lot) => lot.id === id)).filter((lot): lot is PlannerLot => !!lot)
    : lots.filter((lot) => lot.remainingMg > 0).sort((a, b) => (a.purchaseDate < b.purchaseDate ? -1 : a.purchaseDate > b.purchaseDate ? 1 : a.id < b.id ? -1 : 1))
  const totals = {
    metalCost: lots.reduce((s, l) => s + l.metalCost, 0),
    makingCharge: lots.reduce((s, l) => s + l.makingCharge, 0),
    fees: lots.reduce((s, l) => s + l.fees, 0),
  }

  const allocations: Allocation[] = []
  let left = weightMg
  for (const lot of order) {
    if (left <= 0) break
    const take = Math.min(left, lot.remainingMg)
    if (take <= 0) continue
    const portion = (key: "metalCost" | "makingCharge" | "fees") =>
      method === "WEIGHTED_AVERAGE" ? share(totals[key], take, openTotal) : take === lot.remainingMg ? lot[key] : share(lot[key], take, lot.remainingMg)
    allocations.push({ lotId: lot.id, weightMg: take, metalCost: portion("metalCost"), makingCharge: portion("makingCharge"), fees: portion("fees") })
    left -= take
  }
  if (left > 0) throw new InsufficientWeightError()

  const costMetal = allocations.reduce((s, a) => s + a.metalCost, 0)
  const costMaking = allocations.reduce((s, a) => s + a.makingCharge, 0)
  const costFees = allocations.reduce((s, a) => s + a.fees, 0)
  const costBasis = costMetal + costMaking + costFees
  return { allocations, costMetal, costMaking, costFees, costBasis, realisedGain: proceeds - costBasis, realisedGainExcludingMaking: proceeds - (costMetal + costFees) }
}
