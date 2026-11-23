# ADR sync-5 — The record tables are set up for growth and the plain tables go

- Date: 2026-11-23
- Status: accepted
- Task: T-drop-plain-tables

## Context
ADR e2e-1 kept the 27 plain record tables (`ledger_transaction`, `debt`, `plan`, …) so devices
could pull and encrypt their old rows. Every device has done so and the tables are empty; the
server still carried a reader, purge steps and triggers for them. All records now live in
`vault_record`, where every edit rewrites a whole sealed row. With the default autovacuum trigger
(20% dead rows) a large table waits for millions of dead rows before it is cleaned. The recycle-bin
purge hard-deletes records but left their `sync_log` entry pointing at nothing, so those entries
would only accumulate.

## Decision
- Migration V032 drops the plain record tables, their `sync_log` entries and the
  `make_syncable`, `sync_log_row` and `net_worth_snapshot_immutable` routines. It stops with an
  error if any of those tables still holds a row, so a database that has not finished the vault
  migration keeps its data. `sync_conflict` stays: it is empty but live.
- The server no longer reads plain rows: `SyncRowRepository` and `SyncTable.legacyFields()` are
  gone, and pull, attachment visibility and both purges read `vault_record` only. The vault status
  and completion endpoints stay with the same shape and report no plain rows.
- Migration V033 sets autovacuum on `vault_record` and `sync_log` to 2% dead rows plus 1,000, and
  `vault_record` to fillfactor 85 so an edit (which never changes id, household or type) can stay
  on its page as a HOT update.
- Sync housekeeping also deletes `sync_log` entries whose record no longer exists. A device that
  pulls one gets nothing for it anyway, and every device learned of the deletion from the
  soft-delete entry before the purge.

## Consequences
- An old database that still has plain rows cannot pass V032 until its devices finish migrating.
- Hash partitioning of `vault_record` by household is deferred until it nears 50–100 million rows
  or vacuum cannot keep up; it would change the key to `(household_id, id)`. The RUNBOOK lists the
  query that shows when.
