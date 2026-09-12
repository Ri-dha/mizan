import { daysInMonth } from "@/domain/calendar/month"

const PERCENT_SCALE = 10

export interface GoalProjectionResult {
  remaining: number
  percent: number
  monthsToTarget: number | null
  projectedDate: string | null
  requiredMonthly: number | null
}

/** Progress and the two derivable figures of FR-GOL-01/03: months to target, or the monthly amount a date needs. */
export function goalProjection(
  targetAmount: number, saved: number, monthlyContribution: number | null, targetDate: string | null, today: string,
): GoalProjectionResult {
  const remaining = Math.max(0, targetAmount - saved)
  const percent = targetAmount <= 0 || saved >= targetAmount ? 100 : roundedPercent(saved, targetAmount)

  let monthsToTarget: number | null = null
  let projectedDate: string | null = null
  if (monthlyContribution !== null && monthlyContribution > 0) {
    monthsToTarget = Math.ceil(remaining / monthlyContribution)
    projectedDate = plusMonths(today, monthsToTarget)
  }

  let requiredMonthly: number | null = null
  if (targetDate !== null && remaining > 0) {
    const monthsLeft = monthsBetween(today, targetDate)
    requiredMonthly = monthsLeft <= 0 ? remaining : Math.ceil(remaining / monthsLeft)
  }
  return { remaining, percent, monthsToTarget, projectedDate, requiredMonthly }
}

// One decimal place, half-up, in integer arithmetic so both implementations agree.
function roundedPercent(saved: number, target: number): number {
  return Math.floor((saved * 100 * PERCENT_SCALE * 2 + target) / (2 * target)) / PERCENT_SCALE
}

export function plusMonths(date: string, months: number): string {
  const [year, month, day] = date.split("-").map(Number)
  const index = year * 12 + (month - 1) + months
  const y = Math.floor(index / 12)
  const m = (index % 12) + 1
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(Math.min(day, daysInMonth(y, m))).padStart(2, "0")}`
}

/** Whole months between two dates, the way java.time counts them. */
export function monthsBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split("-").map(Number)
  const [ty, tm, td] = to.split("-").map(Number)
  const raw = (ty - fy) * 12 + (tm - fm)
  if (raw > 0 && td < fd) return raw - 1
  if (raw < 0 && td > fd) return raw + 1
  return raw
}
