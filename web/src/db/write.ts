import { db, localTableFor, type Syncable, type SyncTableName } from "./schema"
import { META_KEYS, readMeta, writeMeta } from "./meta"
import { formatHlc, newNodeId, tick, type HlcState } from "@/sync/hlc"

export const ENVELOPE_FIELDS: ReadonlySet<string> = new Set(["id", "ownerId", "clocks"])

async function loadClock(): Promise<HlcState> {
  return (await readMeta<HlcState>(META_KEYS.hlc)) ?? { wall: 0, counter: 0, node: newNodeId() }
}

/** Reserves one fresh stamp inside the current transaction. */
export async function nextStamp(): Promise<string> {
  const advanced = tick(await loadClock())
  await writeMeta(META_KEYS.hlc, advanced)
  return formatHlc(advanced)
}

/**
 * Every local write goes through here: the row is updated, each changed field is stamped,
 * and one outbox op records exactly those fields for the next push. Nothing else writes to
 * a syncable table, which is what keeps the outbox a faithful log.
 */
export async function writeFields<T extends Syncable>(
  table: SyncTableName,
  id: string,
  changes: Partial<Omit<T, keyof Syncable>> & { deletedAt?: string | null },
): Promise<void> {
  const local = localTableFor(table)
  await db.transaction("rw", [local, db.outbox, db.meta], async () => {
    const stamp = await nextStamp()
    const existing = (await local.get(id)) as T | undefined
    const fields = Object.fromEntries(Object.entries(changes).filter(([key]) => !ENVELOPE_FIELDS.has(key)))
    const clocks = Object.fromEntries(Object.keys(fields).map((field) => [field, stamp]))

    await local.put({
      ...(existing ?? { id, ownerId: null, deletedAt: null }),
      ...fields,
      clocks: { ...(existing?.clocks ?? {}), ...clocks },
    } as T)

    await db.outbox.add({
      opId: crypto.randomUUID(),
      table,
      rowId: id,
      fields,
      clocks,
      createdAt: Date.now(),
    })
  })
}

export async function softDelete(table: SyncTableName, id: string) {
  await writeFields(table, id, { deletedAt: new Date().toISOString() })
}

export async function restore(table: SyncTableName, id: string) {
  await writeFields(table, id, { deletedAt: null })
}
