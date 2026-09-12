import { beforeEach, describe, expect, it } from "vitest"

import { liveBucketsOf, livePlans, planFor, saveBuckets } from "./plan"
import { db } from "./schema"

describe("plan versions", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
  })

  it("edits a plan in place in the month it started", async () => {
    const planId = await saveBuckets("2026-09", [{ name: "A", colour: "#000", shareBasisPoints: 10000 }])
    const [bucket] = await liveBucketsOf(planId)
    const again = await saveBuckets("2026-09", [{ id: bucket.id, name: "A renamed", colour: "#000", shareBasisPoints: 10000 }])

    expect(again).toBe(planId)
    expect(await livePlans()).toHaveLength(1)
    expect((await liveBucketsOf(planId))[0]).toMatchObject({ id: bucket.id, name: "A renamed" })
  })

  it("starts a new version for a later month and closes the old one", async () => {
    const first = await saveBuckets("2026-09", [{ name: "A", colour: "#000", shareBasisPoints: 10000 }])
    const second = await saveBuckets("2026-11", [
      { name: "A", colour: "#000", shareBasisPoints: 6000 },
      { name: "B", colour: "#fff", shareBasisPoints: 4000 },
    ])

    const plans = await livePlans()
    expect(second).not.toBe(first)
    expect(plans.find((p) => p.id === first)?.effectiveTo).toBe("2026-10")
    expect(plans.find((p) => p.id === second)).toMatchObject({ effectiveFrom: "2026-11", effectiveTo: null })
    expect(planFor(plans, "2026-10")?.id).toBe(first)
    expect(planFor(plans, "2026-12")?.id).toBe(second)
    expect(await liveBucketsOf(first)).toHaveLength(1)
    expect(await liveBucketsOf(second)).toHaveLength(2)
  })

  it("soft-deletes buckets dropped from the list", async () => {
    const planId = await saveBuckets("2026-09", [
      { name: "A", colour: "#000", shareBasisPoints: 5000 },
      { name: "B", colour: "#fff", shareBasisPoints: 5000 },
    ])
    const [a] = await liveBucketsOf(planId)
    await saveBuckets("2026-09", [{ id: a.id, name: "A", colour: "#000", shareBasisPoints: 10000 }])

    expect(await liveBucketsOf(planId)).toHaveLength(1)
    expect(await db.buckets.count()).toBe(2)
  })
})
