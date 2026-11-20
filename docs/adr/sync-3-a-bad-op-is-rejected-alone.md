# ADR sync-3 — A bad op is rejected alone, not with its batch

- Date: 2026-11-20
- Status: accepted
- Task: T-sync-rejected-ops

## Context
A push applied its whole batch in one transaction, so one op the server refuses (an unknown field,
a plain value, a record of another table, a constraint) failed the request. The device kept every
op of the batch in its outbox and sent the same batch again on every sync, so nothing it wrote
afterwards ever reached the server; the only sign was a growing "changes waiting" count. This
happened during the store screenshots, when a demo loader wrote a field a table does not have, and
it can happen to a real person after a client and the server disagree about a table's fields.

Options: keep the batch atomic and let the client bisect it; send one op per request; or apply each
op on its own and report the refused ones.

## Decision
When the op itself is at fault, the push answers `200` with the op in `rejected` (op id, error
code, detail); the rest of the batch is applied and acknowledged as before. Every such refusal is
found before the op writes anything, so the batch's transaction stays usable: the one database
refusal that could happen mid-write, an id already taken by a row this household cannot see, is
now an `insert … on conflict (id) do nothing` whose missing row count becomes the refusal.
Savepoints were tried first; the JPA dialect of this Spring version does not support them.

The op-level codes are `SYNC_TABLE_UNKNOWN`, `SYNC_FIELD_UNKNOWN`, `SYNC_PLAIN_VALUE_REFUSED`,
`SYNC_TABLE_MISMATCH`, `SYNC_RECORD_IMMUTABLE`, `SYNC_SHARED_WRITE_REFUSED` and
`VALIDATION_FAILED`.

Faults of the request as a whole still fail it: `KEY_ROTATED` (the device fetches the new key and
seals the outbox again, ADR e2e-4), a batch over the limit, a device that is not the caller's, and
a role without the right to push at all.

The client removes rejected ops from its outbox, keeps a record of them on the device (table, row,
code, time; never values) and shows them on the Sync screen, because the change never reached the
person's other devices.

## Consequences
- One bad op no longer stops a device's sync; the person is told which change did not sync.
- A rejected op is not retried. If the server later learns the field, the change is still lost on
  other devices; the person can edit the record again.
- Pushes are no longer all-or-nothing, so a client must not assume the ops of one batch land
  together. No client relied on that: each op already carried its own row and clocks.
- Older clients ignore `rejected`, keep those ops and send them again, but the good ops of each
  batch now get through. An old client whose oldest full batch is entirely rejected would keep
  sending it; the web app and the phone app ship with this change, so none is in the field.
