import { isAfter } from "./hlc"

export interface FieldConflict {
  field: string
  clientValue: unknown
  serverValue: unknown
  clientClock: string
  serverClock: string | undefined
}

export interface MergeOutcome {
  accepted: Record<string, unknown>
  clocks: Record<string, string>
  conflicts: FieldConflict[]
}

/**
 * Per-field last-write-wins, the same rule the server applies. Used when a pulled record
 * meets a local row that may carry unsynced edits: each field keeps whichever side is newer.
 */
export function mergeFields(
  storedFields: Record<string, unknown>,
  storedClocks: Record<string, string>,
  incomingFields: Record<string, unknown>,
  incomingClocks: Record<string, string>,
): MergeOutcome {
  const accepted: Record<string, unknown> = {}
  const clocks = { ...storedClocks }
  const conflicts: FieldConflict[] = []

  for (const [field, incomingValue] of Object.entries(incomingFields)) {
    const incomingClock = incomingClocks[field]
    const storedClock = storedClocks[field]
    if (isAfter(incomingClock, storedClock)) {
      accepted[field] = incomingValue
      clocks[field] = incomingClock
      continue
    }
    const storedValue = storedFields[field]
    if (!sameValue(storedValue, incomingValue)) {
      conflicts.push({ field, clientValue: incomingValue, serverValue: storedValue, clientClock: incomingClock, serverClock: storedClock })
    }
  }

  return { accepted, clocks, conflicts }
}

function sameValue(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a == null || b == null) return a == b
  return JSON.stringify(a) === JSON.stringify(b)
}
