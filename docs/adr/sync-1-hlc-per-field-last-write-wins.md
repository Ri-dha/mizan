# ADR sync-1 — Custom sync: client UUIDs, hybrid logical clocks, per-field last write wins

- Date: 2026-09-13
- Status: accepted
- Task: T-m0-m1

## Context
Records are edited offline on several devices of one household. Options were CRDT libraries,
hosted sync (PowerSync, ElectricSQL) which tie the backend to them, and a small custom protocol.

## Decision
Clients create UUIDs. Every changed field carries a hybrid logical clock stamp compared as a
string. Writes go to an outbox; push is idempotent by op id; pull reads a per-household change
log after a cursor. Merge is per field: newer stamp wins, the losing value is stored as a conflict
for the user to see. Some tables are server-written and read-only to clients.

## Consequences
No external sync service, and edits to different fields of one row never clobber each other.
Concurrent edits to the same field lose silently except for the conflict log. Every syncable
table needs a `SyncTable` bean and `make_syncable`; clients other than the web must implement the
same merge.
