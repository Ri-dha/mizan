import { useSyncExternalStore } from "react"

import { api, ApiError, unwrap } from "@/api/client"
import { uploadPendingBlobs } from "@/attachments/store"
import { runBackupIfDue } from "@/backup/store"
import { loadSession } from "@/api/auth"
import { purgeExpiredDeletions } from "@/db/transactions"
import { refreshQuotes } from "@/market/store"
import { db, localTableFor, SYNC_TABLES, type SyncTableName } from "@/db/schema"
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

async function push(device: string) {
  for (;;) {
    const ops = await db.outbox.orderBy("createdAt").limit(PUSH_BATCH).toArray()
    if (ops.length === 0) return
    const result = await unwrap(api.POST("/api/v1/sync/push", {
      body: { deviceId: device, ops: ops.map(({ opId, table, rowId, fields, clocks }) => ({ opId, table, rowId, fields, clocks })) },
    }))
    await db.transaction("rw", [db.outbox, db.conflicts], async () => {
      await db.outbox.bulkDelete(result.applied)
      for (const conflict of result.conflicts) {
        await db.conflicts.put({
          id: conflict.id,
          table: conflict.table as SyncTableName,
          rowId: conflict.rowId,
          field: conflict.field,
          clientValue: conflict.clientValue,
          serverValue: conflict.serverValue,
          detectedAt: conflict.detectedAt,
          dismissed: false,
        })
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
    await applyRecords(page.records as PulledRecord[])
    await writeMeta(META_KEYS.pullCursor, page.nextSeq)
    if (!page.hasMore) return
  }
}

type PulledRecord = { table: string; rowId: string; ownerId: string; fields: Record<string, unknown>; clocks: Record<string, string> }

/**
 * Applies pulled rows with the same per-field rule the server uses, so an edit made here
 * while offline survives a pull that carries an older value for that field.
 */
async function applyRecords(records: PulledRecord[]) {
  const tables = Object.values(SYNC_TABLES).map((name) => db.table(name))
  await db.transaction("rw", [...tables, db.meta], async () => {
    let clock = (await readMeta<HlcState>(META_KEYS.hlc)) ?? { wall: 0, counter: 0, node: newNodeId() }
    for (const record of records) {
      if (!(record.table in SYNC_TABLES)) continue
      const local = localTableFor(record.table as SyncTableName)
      const existing = await local.get(record.rowId)
      const storedFields: Record<string, unknown> = existing ? { ...existing } : {}
      const outcome = mergeFields(storedFields, existing?.clocks ?? {}, record.fields, record.clocks)
      await local.put({
        ...(existing ?? { id: record.rowId, deletedAt: null }),
        ...outcome.accepted,
        ownerId: record.ownerId,
        clocks: outcome.clocks,
      })
      for (const stamp of Object.values(record.clocks)) clock = observe(clock, stamp)
    }
    await writeMeta(META_KEYS.hlc, clock)
  })
}

export async function syncNow(): Promise<void> {
  if (running) return running
  running = (async () => {
    if (!navigator.onLine) {
      update({ phase: "offline" })
      return
    }
    update({ phase: "syncing", lastError: null })
    try {
      const device = await deviceId()
      await push(device)
      await pull(device)
      await uploadPendingBlobs()
      await push(device)
      await purgeExpiredDeletions()
      await refreshQuotes()
      await runBackupIfDue(await loadSession())
      const now = Date.now()
      await writeMeta(META_KEYS.lastSyncAt, now)
      update({ phase: "idle", lastSyncAt: now })
    } catch (error) {
      const message = error instanceof ApiError ? error.code : String(error)
      update({ phase: error instanceof ApiError && error.status === 0 ? "offline" : "error", lastError: message })
    } finally {
      await refreshPending()
      running = null
    }
  })()
  return running
}

/** Wires the triggers once: reconnect, tab focus, a local write, and a slow periodic tick. */
export function startSyncEngine() {
  if (started) return
  started = true
  void readMeta<number>(META_KEYS.lastSyncAt).then((at) => update({ lastSyncAt: at ?? null }))
  void refreshPending()

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
  update({ phase: navigator.onLine ? "idle" : "offline", pending: 0, lastSyncAt: null, lastError: null })
}
