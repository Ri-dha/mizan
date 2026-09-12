import { db, type Bucket, type Plan } from "./schema"
import { writeFields } from "./write"
import { shiftKey } from "@/domain/calendar/month"

export interface BucketInput {
  id?: string
  name: string
  colour: string
  shareBasisPoints: number
}

export async function livePlans(): Promise<Plan[]> {
  return db.plans.filter((plan) => plan.deletedAt === null).toArray()
}

export async function liveBucketsOf(planId: string | undefined): Promise<Bucket[]> {
  if (!planId) return []
  return db.buckets.where("planId").equals(planId).filter((b) => b.deletedAt === null).sortBy("sortOrder")
}

/** The version in force for a month: latest effectiveFrom at or before it, not yet ended. */
export function planFor(plans: Plan[], monthKey: string): Plan | undefined {
  return plans
    .filter((plan) => plan.effectiveFrom <= monthKey && (plan.effectiveTo === null || plan.effectiveTo >= monthKey))
    .sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? 1 : -1))[0]
}

/**
 * Saves a month's buckets. A plan that started this month is edited in place; one that
 * started earlier is closed at the previous month and a new version begins here, so the
 * months already run keep their percentages (FR-PLN-06).
 */
export async function saveBuckets(monthKey: string, buckets: BucketInput[]): Promise<string> {
  return db.transaction("rw", [db.plans, db.buckets, db.outbox, db.meta], async () => {
    const current = planFor(await livePlans(), monthKey)
    const planId = current && current.effectiveFrom === monthKey ? current.id : await startVersion(current, monthKey)
    const existing = planId === current?.id ? await liveBucketsOf(planId) : []
    const keep = new Set<string>()

    for (const [sortOrder, bucket] of buckets.entries()) {
      const id = bucket.id && existing.some((b) => b.id === bucket.id) ? bucket.id : crypto.randomUUID()
      keep.add(id)
      await writeFields<Bucket>("bucket", id, {
        planId,
        name: bucket.name,
        colour: bucket.colour,
        shareBasisPoints: bucket.shareBasisPoints,
        sortOrder,
        ...(existing.some((b) => b.id === id) ? {} : { visibility: "SHARED" as const, fixedAmount: null, carryOver: false }),
      })
    }
    for (const gone of existing.filter((b) => !keep.has(b.id))) {
      await writeFields<Bucket>("bucket", gone.id, { deletedAt: new Date().toISOString() })
    }
    return planId
  })
}

async function startVersion(previous: Plan | undefined, monthKey: string): Promise<string> {
  if (previous) {
    await writeFields<Plan>("plan", previous.id, { effectiveTo: shiftKey(monthKey, -1) })
  }
  const id = crypto.randomUUID()
  await writeFields<Plan>("plan", id, { visibility: "SHARED", effectiveFrom: monthKey, effectiveTo: null })
  return id
}
