/** Mirrors HouseholdRole on the server; the server still enforces every one of these. */
const HOUSEHOLD_VIEW = new Set(["OWNER", "MEMBER", "VIEWER"])
const RECORD_WRITE = new Set(["OWNER", "MEMBER", "DEPENDENT"])

export function mayHoldHouseholdKey(role: string): boolean {
  return HOUSEHOLD_VIEW.has(role)
}

export function mayWriteRecords(role: string): boolean {
  return RECORD_WRITE.has(role)
}

/** Shared rows need both: seeing the household and writing to it. */
export function mayWriteShared(role: string): boolean {
  return HOUSEHOLD_VIEW.has(role) && RECORD_WRITE.has(role)
}
