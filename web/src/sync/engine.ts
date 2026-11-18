import { useSyncExternalStore } from "react"

import { api, ApiError, unwrap } from "@/api/client"
import { uploadPendingBlobs } from "@/attachments/store"
import { runBackupIfDue } from "@/backup/store"
import { loadSession, refreshProfile, type LocalSession } from "@/api/auth"
import { purgeExpiredDeletions } from "@/db/transactions"
import { refreshQuotes } from "@/market/store"
import { db, localTableFor, SYNC_TABLES, type Syncable, type SyncTableName } from "@/db/schema"
import { onVaultReady, vaultReady } from "@/vault/account"
import { refreshGrants, shareKeysWithMembers } from "@/vault/keyring"
import { openFields, openValue, sealFields } from "@/vault/records"
import { mayHoldHouseholdKey } from "@/app/roles"
import { completeMigrationIfDrained, runDeviceDuties } from "./duties"
import { META_KEYS, readMeta, writeMeta } from "@/db/meta"
import { newNodeId, observe, type HlcState } from "./hlc"
import { mergeFields } from "./merge"

export type SyncPhase = "idle" | "offline" | "syncing" | "error"

export interface SyncStatus {
  phase: SyncPhase
  pending: number
  lastSyncAt: number | null
  lastError: string | null
}

const PUSH_BATCH = 200
const PULL_LIMIT = 500
const PERIODIC_MS = 5 * 60 * 1000

let status: SyncStatus = { phase: navigator.onLine ? "idle" : "offline", pending: 0, lastSyncAt: null, lastError: null }
const listeners = new Set<() => void>()
let running: Promise<void> | null = null
let started = false

function update(patch: Partial<SyncStatus>) {
  status = { ...status, ...patch }
  listeners.forEach((listener) => listener())
}

export function useSyncStatus(): SyncStatus {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => status,
  )
}

async function refreshPending() {
  update({ pending: await db.outbox.count() })
}

async function deviceId(): Promise<string> {
  const known = await readMeta<string>(META_KEYS.deviceId)
  if (known) return known
  const device = await unwrap(api.POST("/api/v1/sync/devices", { body: { name: describeDevice() } }))
  await writeMeta(META_KEYS.deviceId, device.id)
  return device.id
}

function describeDevice(): string {
  const ua = navigator.userAgent
  const platform = /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS" : /Mac/.test(ua) ? "Mac" : /Windows/.test(ua) ? "Windows" : "Web"
  return `${platform} browser`
}

const APPEND_ONLY: ReadonlySet<string> = new Set(["net_worth_snapshot"])

type QueuedOp = { opId: string; table: SyncTableName; rowId: string; fields: Record<string, unknown>; clocks: Record<string, string> }

/** Seals what can be sealed now; ops for a key not yet granted stay queued for a later sync. */
async function sendableOps(): Promise<QueuedOp[]> {
  const ready: QueuedOp[] = []
  for (const op of await db.outbox.orderBy("createdAt").toArray()) {
    const row = (await localTableFor(op.table).get(op.rowId)) as (Syncable & { visibility?: string }) | undefined
    if (APPEND_ONLY.has(op.table) && row?.vaulted) {
      await db.outbox.delete(op.opId)
      continue
    }
    const visibility = (op.fields.visibility as string | undefined) ?? row?.visibility
    const sealed = await sealFields(op.table, op.rowId, visibility, op.fields)
    if (!sealed) continue
    ready.push({ opId: op.opId, table: op.table, rowId: op.rowId, fields: sealed, clocks: op.clocks })
    if (ready.length === PUSH_BATCH) break
  }
  return ready
}

async function push(device: string) {
  for (;;) {
    const ops = await sendableOps()
    if (ops.length === 0) return
    const result = await unwrap(api.POST("/api/v1/sync/push", { body: { deviceId: device, ops } }))
    const conflicts = await Promise.all(result.conflicts.map(async (conflict) => ({
      id: conflict.id,
      table: conflict.table as SyncTableName,
      rowId: conflict.rowId,
      field: conflict.field,
      clientValue: await openValue(conflict.table, conflict.rowId, conflict.field, conflict.clientValue),
      serverValue: await openValue(conflict.table, conflict.rowId, conflict.field, conflict.serverValue),
      detectedAt: conflict.detectedAt,
      dismissed: false,
    })))
    const applied = new Set(result.applied)
    const tables = Object.values(SYNC_TABLES).map((name) => db.table(name))
    await db.transaction("rw", [db.outbox, db.conflicts, ...tables], async () => {
      await db.outbox.bulkDelete(result.applied)
      for (const conflict of conflicts) await db.conflicts.put(conflict)
      for (const op of ops) {
        if (applied.has(op.opId) && APPEND_ONLY.has(op.table)) await localTableFor(op.table).update(op.rowId, { vaulted: true })
      }
    })
    await refreshPending()
    if (ops.length < PUSH_BATCH) return
  }
}

async function pull(device: string) {
  for (;;) {
    const since = (await readMeta<number>(META_KEYS.pullCursor)) ?? 0
    const page = await unwrap(api.GET("/api/v1/sync/pull", { params: { query: { deviceId: device, since, limit: PULL_LIMIT } } }))
    await receive(page.records as PulledRecord[])
    await writeMeta(META_KEYS.pullCursor, page.nextSeq)
    if (!page.hasMore) return
  }
}

type PulledRecord = { table: string; rowId: string; ownerId: string; fields: Record<string, unknown>; clocks: Record<string, string>; encrypted: boolean }
type OpenRecord = Omit<PulledRecord, "encrypted"> & { vaulted: boolean }

const inboxKey = (record: { table: string; rowId: string }) => `${record.table}|${record.rowId}`

/** Legacy rows (before ADR e2e-1) arrive plain until their owner's device has re-sent them sealed. */
async function receive(records: PulledRecord[]) {
  const opened: OpenRecord[] = []
  const waiting: PulledRecord[] = []
  for (const record of records) {
    if (!(record.table in SYNC_TABLES)) continue
    if (!record.encrypted) {
      opened.push({ ...record, vaulted: false })
      continue
    }
    const result = await openFields(record.table, record.rowId, record.fields)
    if (result.status === "waiting") waiting.push(record)
    else opened.push({ ...record, fields: result.fields, vaulted: true })
  }
  if (waiting.length > 0) {
    await db.sealedInbox.bulkPut(waiting.map(({ table, rowId, ownerId, fields, clocks }) => ({ key: inboxKey({ table, rowId }), record: { table, rowId, ownerId, fields, clocks } })))
  }
  await applyRecords(opened)
}

async function retrySealed() {
  const parked = await db.sealedInbox.toArray()
  if (parked.length === 0) return
  const opened: OpenRecord[] = []
  const done: string[] = []
  for (const { key, record } of parked) {
    const result = await openFields(record.table, record.rowId, record.fields)
    if (result.status === "waiting") continue
    opened.push({ ...record, fields: result.fields, vaulted: true })
    done.push(key)
  }
  await applyRecords(opened)
  await db.sealedInbox.bulkDelete(done)
}

/**
 * Applies pulled rows with the same per-field rule the server uses, so an edit made here
 * while offline survives a pull that carries an older value for that field.
 */
async function applyRecords(records: OpenRecord[]) {
  if (records.length === 0) return
  const tables = Object.values(SYNC_TABLES).map((name) => db.table(name))
  await db.transaction("rw", [...tables, db.meta, db.sealedInbox], async () => {
    let clock = (await readMeta<HlcState>(META_KEYS.hlc)) ?? { wall: 0, counter: 0, node: newNodeId() }
    for (const record of records) {
      const local = localTableFor(record.table as SyncTableName)
      const existing = await local.get(record.rowId)
      const storedFields: Record<string, unknown> = existing ? { ...existing } : {}
      const outcome = mergeFields(storedFields, existing?.clocks ?? {}, record.fields, record.clocks)
      await local.put({
        ...(existing ?? { id: record.rowId, deletedAt: null }),
        ...outcome.accepted,
        ownerId: record.ownerId,
        clocks: outcome.clocks,
        ...(record.vaulted || existing?.vaulted ? { vaulted: true } : {}),
      })
      if (record.vaulted) await db.sealedInbox.delete(inboxKey(record))
      for (const stamp of Object.values(record.clocks)) clock = observe(clock, stamp)
    }
    await writeMeta(META_KEYS.hlc, clock)
  })
}

async function exchangeKeys(session: LocalSession) {
  await refreshGrants()
  if (mayHoldHouseholdKey(session.role)) await shareKeysWithMembers(session.role === "OWNER")
}

export async function syncNow(): Promise<void> {
  if (running) return running
  running = (async () => {
    if (!navigator.onLine) {
      update({ phase: "offline" })
      return
    }
    // Keys open a moment after sign-in; onVaultReady starts this again then, so it is not a failure.
    if (!vaultReady()) return
    update({ phase: "syncing", lastError: null })
    try {
      if (!(await loadSession())) return
      await refreshProfile()
      const session = await loadSession()
      if (!session) return
      const device = await deviceId()
      await exchangeKeys(session)
      await retrySealed()
      await push(device)
      await pull(device)
      await runDeviceDuties(session)
      await uploadPendingBlobs()
      await push(device)
      await completeMigrationIfDrained(session)
      await purgeExpiredDeletions()
      await refreshQuotes()
      await runBackupIfDue(session)
      const now = Date.now()
      await writeMeta(META_KEYS.lastSyncAt, now)
      update({ phase: "idle", lastSyncAt: now })
    } catch (error) {
      const message = error instanceof ApiError ? error.code : String(error)
      update({ phase: error instanceof ApiError && error.status === 0 ? "offline" : "error", lastError: message })
    } finally {
      await refreshPending()
    }
  })().finally(() => {
    running = null
  })
  return running
}

/** Wires the triggers once: reconnect, tab focus, a local write, and a slow periodic tick. */
export function startSyncEngine() {
  if (started) return
  started = true
  void readMeta<number>(META_KEYS.lastSyncAt).then((at) => update({ lastSyncAt: at ?? null }))
  void refreshPending()

  onVaultReady(() => void syncNow())
  window.addEventListener("online", () => void syncNow())
  window.addEventListener("offline", () => update({ phase: "offline" }))
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void syncNow()
  })
  setInterval(() => void syncNow(), PERIODIC_MS)

  db.outbox.hook("creating", () => {
    queueMicrotask(() => {
      void refreshPending()
      scheduleSoon()
    })
  })
  void syncNow()
}

let debounce: ReturnType<typeof setTimeout> | undefined
function scheduleSoon() {
  clearTimeout(debounce)
  debounce = setTimeout(() => void syncNow(), 800)
}

export async function resetSyncState() {
  await db.meta.bulkDelete([META_KEYS.deviceId, META_KEYS.pullCursor, META_KEYS.lastSyncAt])
  await db.sealedInbox.clear()
  update({ phase: navigator.onLine ? "idle" : "offline", pending: 0, lastSyncAt: null, lastError: null })
}
