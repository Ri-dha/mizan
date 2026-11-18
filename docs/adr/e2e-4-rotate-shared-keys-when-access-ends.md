# ADR e2e-4 — Rotate a shared key when someone's access to it ends

- Date: 2026-11-19
- Status: accepted
- Task: T-e2e-key-rotation

## Context
ADR e2e-1 left a gap: a member who leaves keeps the household key version they were granted,
so records written after they leave are sealed with a key they hold. They can no longer reach
the server (row-level security), but a leaked database or backup would open to that key. The
keyring already holds several versions per key and seals with the newest; the outbox seals at
push time. Access ends in five ways: removal, leaving, a role that may no longer hold the key,
an advisor's access lapsing (memberships expire lazily, by query, with no event), and account
deletion (which deleted the person's grant rows with the account).

## Decision
- **When a rotation is due is computed, not flagged.** A key kind is due in a household when any
  grant of its current version belongs to someone who is not an active member allowed to hold it,
  or to a deleted account. Grants are no longer deleted with the account: `user_id` becomes
  nullable and is set to null, so the evidence survives. Members and owners hold both keys,
  advisors only the report key, dependents neither.
- **`household_key_state`** records each household's current version per kind. Existing
  households start at their highest granted version; the first grant of version 1 creates the
  row; starting over as the sole key holder deletes it with the grants (ADR e2e-3).
- **A remaining member's device rotates** (`POST /keys/household/rotate`): it must hold the
  current version, the rotation must be due, the new version must be exactly current + 1, and it
  must carry a grant for every remaining member who may hold the key and has a public key. The
  state row is locked, so of two devices rotating at once one wins and the other gets a conflict.
  On success the version advances and every grant of people who lost access is deleted.
- **Writes under an old version are refused**: once version N+1 exists, a pushed field sealed `h`
  or `r` with any other version fails the whole push with `KEY_ROTATED`. The device fetches its
  grants and pushes again; its outbox holds plain values and seals them with the newest key.
- **Old records are not re-encrypted.** The person leaving has already seen them, and rewriting
  every shared row would race with members' edits. A field is resealed under the new key when it
  is next edited.
- **New members receive every version**, not only the newest, so history stays readable.

## Consequences
- After rotation, a leaked database opens to an ex-member's key only for what was written before
  they left. What they saw while a member stays with them; the app says so when someone leaves.
- Rotation waits until a remaining key holder's device syncs; writes in between use the old key.
- A device that misses a rotation has one push refused, then recovers by itself.
- The sealed-field format is unchanged; the test vectors still hold.
