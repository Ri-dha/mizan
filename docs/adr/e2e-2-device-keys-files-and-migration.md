# ADR e2e-2 — Device key storage, receipt keys and migration details

- Date: 2026-11-18
- Status: accepted
- Task: T-e2e-encryption
- Supersedes: the "kept wrapped by the existing PIN data key" sentence of ADR e2e-1

## Context
Building ADR e2e-1 raised questions it left open or answered in a way that does not work:
- Sync runs while the app is locked and on devices with no PIN, so the account and household keys
  cannot be locked away behind the PIN data key.
- Receipts were sealed with each device's own key, so no other member could open them.
- Sign-in has to tell a legacy account from a wrong password without a pre-login lookup, which
  would reveal whether an account exists.
- Switching household wiped every local table, which now also wiped the account's keys and the PIN.
- Migrating shared rows and append-only snapshots needs rules the server can enforce.

## Decision
- **Keys at rest on a device** are serialised and sealed with AES-GCM under a non-extractable
  device key that IndexedDB keeps as an opaque `CryptoKey` (`vault.deviceKey`, `vault.keyring`).
  The PIN still guards the screen (ADR lock-1); it does not guard the keys.
- **Receipts and other files** each get a random AES-GCM key. It travels as the `blobKey` field
  of the attachment row, so it is sealed with the row. The attachment table declares `blobKey` as
  sealed-only (`SyncTable.legacyFields()` leaves it out of the old plain table). Files from before
  this have no `blobKey` and still open with the device key. Storage receives
  `application/octet-stream` and never learns the file type.
- **Legacy sign-in**: the client signs in with the auth key. Only if that is refused does it retry
  once with `legacyPassword`. So a typed password reaches the server only when it does not
  produce a valid auth key. The fallback is removed once no `LEGACY` accounts remain.
- **The salt is the normalised identifier** (`normaliseIdentifier` mirrors `Identifier.parse`).
  This holds because an account has exactly one identifier. Adding a second identifier needs a
  per-account salt first.
- **Bootstrapping and granting**: the owner's device creates household and report key version 1
  when nobody holds them. Any device holding a key grants it to every member allowed to hold it
  who has a public key and lacks that version. A grant on the server overrides a local copy of the
  same version, so two owner devices bootstrapping at once converge on the first.
- **Pending records**: a pulled record sealed under a key this device lacks waits in
  `sealedInbox` and is retried after each grant refresh. Ops that cannot be sealed yet stay in the
  outbox.
- **Changing visibility** re-sends every field with a fresh stamp under the new key, so the old
  audience's copy is overwritten rather than left readable.
- **Migration**: each device re-sends, sealed, the rows it answers for: its owner's rows, plus
  every shared row when the household owner signs in. It calls `/vault/migration/complete` only
  after the outbox has drained. Shared rows re-sent by the owner become owned by the owner.
  Snapshots already in the vault are never re-sent, because the server refuses changes to
  append-only records.
- **Device-built records** use deterministic ids from SHA-256 of their identity: the default plan
  and buckets, each member's contribution, each year's annual report, and a month's close row.
  Devices doing the same work converge on one row. A snapshot keeps a random id because it is
  append-only.
- **Month close** runs on the first device to sync after a month ends. It covers only months from
  the household's first plan onwards.
- **Changing household** keeps the PIN, biometrics, lock delay and the account's own keys, and
  drops the old household's rows and shared keys.

## Consequences
- Someone with code running in the origin, or with the device unlocked, can use the keys. Local
  data at rest is still `T-local-encryption`.
- Dependents cannot write shared records, so their private holdings are not part of the household
  figure (the server counted them before ADR e2e-1).
- A member who joins sees shared rows only after a device that holds the household key has
  synced; until then they wait in `sealedInbox`.
- A user who loses the recovery code after a password reset cannot open their keys, and there is
  no way yet to start over with new ones (the server refuses a second key set). That path is
  T-e2e-key-reset.
