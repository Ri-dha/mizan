/** Basis points per year → the divisor that turns balance × bp into monthly interest. */
const MONTHLY_DIVISOR = 10_000 * 12
const MAX_MONTHS = 1200

export interface PayoffResult {
  neverClears: boolean
  months: number
  totalPaid: number
  totalInterest: number
}

const NEVER: PayoffResult = { neverClears: true, months: 0, totalPaid: 0, totalInterest: 0 }

/**
 * Amortisation by simulation in whole minor units (FR-DBT-03): each month charges interest at
 * the annual rate over twelve, rounded half-up, then applies the payment. Simulating rather
 * than solving the closed form is what lets the client and the server agree to the dinar.
 */
export function debtPayoff(balance: number, annualRateBasisPoints: number, monthlyPayment: number): PayoffResult {
  if (balance <= 0) return { neverClears: false, months: 0, totalPaid: 0, totalInterest: 0 }
  let months = 0
  let paid = 0
  let interestTotal = 0
  let remaining = balance
  while (remaining > 0) {
    const interest = monthlyInterest(remaining, annualRateBasisPoints)
    if (monthlyPayment <= interest || months >= MAX_MONTHS) return NEVER
    months += 1
    const due = Math.min(monthlyPayment, remaining + interest)
    paid += due
    interestTotal += interest
    remaining = remaining + interest - due
  }
  return { neverClears: false, months, totalPaid: paid, totalInterest: interestTotal }
}

export function monthlyInterest(balance: number, annualRateBasisPoints: number): number {
  return Math.floor((balance * annualRateBasisPoints + MONTHLY_DIVISOR / 2) / MONTHLY_DIVISOR)
}
