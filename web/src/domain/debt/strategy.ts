import { monthlyInterest } from "./payoff"

const MAX_MONTHS = 1200

export interface StrategyDebt {
  id: string
  balance: number
  annualRateBasisPoints: number
  monthlyPayment: number
}

export interface StrategyOutcome {
  order: string[]
  months: number
  totalInterest: number
  neverClears: boolean
}

export interface StrategyResult {
  snowball: StrategyOutcome
  avalanche: StrategyOutcome
}

/**
 * FR-DBT-07: the same debts paid in two orders. Every debt gets its own payment each month; the
 * extra amount and the payments of debts already cleared go to the first open debt in the order.
 * A comparison of outcomes, never advice.
 */
export function compareStrategies(debts: StrategyDebt[], extraMonthly: number): StrategyResult {
  const byId = (a: StrategyDebt, b: StrategyDebt) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)
  const snowball = [...debts].sort((a, b) => a.balance - b.balance || byId(a, b)).map((d) => d.id)
  const avalanche = [...debts].sort((a, b) => b.annualRateBasisPoints - a.annualRateBasisPoints || a.balance - b.balance || byId(a, b)).map((d) => d.id)
  return { snowball: simulate(debts, extraMonthly, snowball), avalanche: simulate(debts, extraMonthly, avalanche) }
}

export function simulate(debts: StrategyDebt[], extraMonthly: number, order: string[]): StrategyOutcome {
  const debt = Object.fromEntries(debts.map((d) => [d.id, d]))
  const balance = Object.fromEntries(debts.map((d) => [d.id, d.balance]))
  let months = 0
  let totalInterest = 0
  while (order.some((id) => balance[id] > 0)) {
    if (months >= MAX_MONTHS) return { order, months: 0, totalInterest: 0, neverClears: true }
    months += 1
    let freed = extraMonthly
    for (const id of order) {
      if (balance[id] > 0) {
        const interest = monthlyInterest(balance[id], debt[id].annualRateBasisPoints)
        totalInterest += interest
        balance[id] += interest
      }
    }
    for (const id of order) {
      if (balance[id] <= 0) freed += debt[id].monthlyPayment
      else balance[id] -= Math.min(debt[id].monthlyPayment, balance[id])
    }
    for (const id of order) {
      if (balance[id] > 0 && freed > 0) {
        const use = Math.min(freed, balance[id])
        balance[id] -= use
        freed -= use
      }
    }
  }
  return { order, months, totalInterest, neverClears: false }
}
