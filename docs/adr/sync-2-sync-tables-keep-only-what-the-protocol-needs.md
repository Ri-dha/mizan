# ADR sync-2 — The sync tables keep only what the protocol needs

- Date: 2026-11-19
- Status: accepted
- Task: T-trim-tables-and-cache

## Context
Three tables only grew. `sync_log` gets a row for every change of every record; a pull returns
each changed record once, as it is now, so older entries for the same record are never needed.
`sync_op_receipt` keeps every pushed op id so a retry is not applied twice, but a device only
retries while it is still waiting for its answer. `audit_entry` received a row for every synced
op recording only the table and the field names of encrypted data, and its trigger forbids
deletion. The owner chose to stop the per-op audit rows (2026-11-19).

## Decision
- A nightly job (`SyncHousekeeping`, 03:40, under the job lock) deletes every `sync_log` entry
  that a newer entry for the same record supersedes, and receipts older than
  `mizan.sync.receipt-retention` (30 days).
- A field arriving with exactly the clock already stored is the same write sent again: it is
  acknowledged without a change or a conflict. An append-only record arriving with identical
  clocks is acknowledged too. Devices seal again on every push, so a retry's ciphertext differs;
  the clock (unique per device write) is what identifies it. A real rewrite carries newer clocks
  and an append-only record still refuses it.
- Synced records are no longer audited one by one. Security events (sign-in, keys, membership,
  operator actions) stay audited. Existing rows stay: the table is append-only by design.

## Consequences
- `sync_log` holds about one row per record and `sync_op_receipt` a month of pushes.
- A device that retries after its receipt is gone is still safe.
- The audit log no longer shows which records changed when; the sync log and clocks still order
  changes for devices.
