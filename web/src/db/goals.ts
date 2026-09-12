import { db, type Goal, type GoalDeposit, type Visibility } from "./schema"
import { softDelete, writeFields } from "./write"
import { toBaseAmount } from "./income"
import { createTransaction, deleteTransaction } from "./transactions"

export interface GoalInput {
  name: string
  targetAmount: number
  currency: string
  targetDate: string | null
  monthlyContribution: number | null
  bucketId: string | null
  note: string | null
  visibility: Visibility
}

export async function createGoal(input: GoalInput): Promise<string> {
  const id = crypto.randomUUID()
  const sortOrder = await db.goals.count()
  await writeFields<Goal>("goal", id, { ...input, backingAssetType: null, backingAssetId: null, status: "ACTIVE", completedOn: null, sortOrder })
  return id
}

export async function updateGoal(id: string, input: Partial<GoalInput>) {
  await writeFields<Goal>("goal", id, input)
}

export function liveGoals() {
  return db.goals.filter((g) => g.deletedAt === null).sortBy("sortOrder")
}

export function liveDeposits() {
  return db.goalDeposits.filter((d) => d.deletedAt === null).sortBy("depositedOn")
}

/** FR-GOL-02: progress is the deposit ledger. */
export function savedOf(goal: Goal, deposits: GoalDeposit[]): number {
  return deposits
    .filter((d) => d.goalId === goal.id)
    .reduce((sum, d) => sum + (d.direction === "DEPOSIT" ? d.amount : -d.amount), 0)
}

export async function recordDeposit(goal: Goal, deposits: GoalDeposit[], amount: number, direction: GoalDeposit["direction"], on: string, monthKey: string, note: string | null) {
  await db.transaction("rw", db.tables, async () => {
    const transactionId = await createTransaction({
      type: "TRANSFER",
      occurredOn: on,
      monthKey,
      amount,
      currency: goal.currency,
      fxRateMicros: 1_000_000,
      bucketId: direction === "DEPOSIT" ? goal.bucketId : null,
      category: null,
      payee: goal.name,
      note,
      counterpartyType: direction === "DEPOSIT" ? "GOAL" : goal.bucketId ? "BUCKET" : null,
      counterpartyId: direction === "DEPOSIT" ? goal.id : goal.bucketId,
      attachmentId: null,
      visibility: goal.visibility,
    })
    await writeFields<GoalDeposit>("goal_deposit", crypto.randomUUID(), {
      visibility: goal.visibility, goalId: goal.id, depositedOn: on, monthKey, amount, direction, transactionId, note,
    })
    const saved = savedOf(goal, deposits) + (direction === "DEPOSIT" ? amount : -amount)
    if (saved >= goal.targetAmount && goal.status === "ACTIVE") {
      await writeFields<Goal>("goal", goal.id, { status: "COMPLETED", completedOn: on })
    } else if (saved < goal.targetAmount && goal.status === "COMPLETED") {
      await writeFields<Goal>("goal", goal.id, { status: "ACTIVE", completedOn: null })
    }
  })
}

export async function undoDeposit(deposit: GoalDeposit) {
  await db.transaction("rw", db.tables, async () => {
    if (deposit.transactionId) await deleteTransaction(deposit.transactionId)
    await softDelete("goal_deposit", deposit.id)
  })
}

/** A planned monthly deposit commits its bucket until the month's deposit is made (BR-03). */
export function committedByGoals(goals: Goal[], deposits: GoalDeposit[], monthKey: string): Record<string, number> {
  const committed: Record<string, number> = {}
  for (const goal of goals) {
    if (goal.status !== "ACTIVE" || !goal.bucketId || !goal.monthlyContribution) continue
    if (deposits.some((d) => d.goalId === goal.id && d.monthKey === monthKey && d.direction === "DEPOSIT")) continue
    committed[goal.bucketId] = (committed[goal.bucketId] ?? 0) + toBaseAmount(goal.monthlyContribution, 1_000_000)
  }
  return committed
}
