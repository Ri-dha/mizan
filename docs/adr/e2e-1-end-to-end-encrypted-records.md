# ADR e2e-1 — End-to-end encryption of every financial record

- Date: 2026-11-18
- Status: accepted
- Task: T-e2e-encryption
- Supersedes: the server-computed parts of ADR tenancy-1 and prices-1 (household totals, month
  close snapshots, reports and notification rules computed on the server)

## Context
A direct database query showed balances, salaries and net worth in plain text. Row-level security
stops other users but not whoever holds the database, a backup or the host. The owner chose full
end-to-end encryption: the server must not be able to read any amount, name or note. Three
options were weighed: disk and backup encryption (stops a stolen disk only), encrypting free-text
columns (amounts stay readable), and end-to-end encryption of every field (the server can no
longer calculate). The third was chosen.

## Decision
**Keys** (all WebCrypto; nothing secret leaves a device unwrapped):
- Each user has a random **user key** (AES-GCM 256) and an **ECDH P-256 key pair**. The user key
  is stored on the server wrapped twice: by a key derived from the password, and by a **recovery
  key** shown once. The private key is stored encrypted with the user key.
- Each household has a **household key** and a **report key** (AES-GCM 256, versioned). They are
  granted to members by wrapping them to the member's public key (ephemeral ECDH, HKDF, AES-GCM).
  Members get both; advisors get only the report key; dependents get neither.
- On a device the user and household keys are kept wrapped by the existing PIN data key
  (ADR lock-1).

**Passwords**: the client derives a master key with PBKDF2-SHA256 (600,000 iterations, salt from
the normalised identifier). HKDF splits it into an **auth key**, which is what the server receives
and hashes with Argon2id, and a **wrapping key**, which never leaves the device. The server never
sees the password, so it cannot derive the wrapping key. Accounts created before this send their
legacy password once; the server verifies it and switches to the auth key.

**Records**: every syncable row is stored in one table, `vault_record`, as `table_name`, `id`,
`household_id`, `owner_id`, `visibility`, `deleted_at`, per-field clocks, and per-field
ciphertext. Only `visibility` and `deletedAt` stay plain (row-level security and the recycle-bin
purge need them). Each value is encrypted separately as JSON, padded to 32-byte blocks, with
AES-GCM, a random IV and the record id, table and field as additional data, in the format
`e1.<h|u|r>.<keyVersion>.<base64url>`: `h` household key (shared rows), `u` the owner's user key
(private rows), `r` the report key (advisor reports). Per-field last-write-wins merge (ADR sync-1)
is unchanged because the server compares clocks, not values. Net worth snapshots are append-only.

**What moves to the devices**: month close and its snapshot, the household net worth (each
member's device publishes an encrypted total of its private rows so members see the household
figure without the items, BR-16), the annual report for advisors, the default plan, and
notification rules. Devices upload a schedule of reminder times and kinds; the server sends
generic text at those times ("A bill is due today") and never knows which bill or how much.

**Migration**: existing plain rows stay readable by sync pull until the owner's device has
encrypted and re-uploaded them; each member's device migrates its own private rows; the server
then deletes the plain rows. Plain pushes are refused from now on, so an old cached web app
cannot write unencrypted data.

## Consequences
- A database dump, backup or log contains ciphertext, record counts, timestamps and table names,
  but no amounts, names, notes or relationships between records.
- **A user who forgets the password and loses the recovery key loses their data.** A password
  reset by SMS or email restores sign-in, not the keys; the recovery key restores the keys.
- The server can no longer compute anything from records: the Java domain package and the
  server calculators are removed. `shared/test-vectors/` stays as the contract for every client
  (web now, Flutter later).
- Month close and notifications depend on a member opening the app. Notification text is
  generic.
- A removed member keeps the household key they already had; key rotation on removal is a
  follow-up (T-e2e-key-rotation).
- Data on the device itself is still plain inside IndexedDB, protected by the operating system and
  the PIN screen (T-local-encryption).
- Every client must implement the same key hierarchy, field format and additional data.
