# T-e2e-key-rotation — Rotate a shared key when someone's access to it ends
- Status: in-review (2026-11-19); server on feat/key-rotation in MizanApi, core and web on
  feat/key-rotation in MizanApp (worktree ../.wt-key-rotation), uncommitted
- Context: ADR e2e-4 (closes the gap left by ADR e2e-1). Owner agreed: old records are not
  re-encrypted; a remaining member's device rotates automatically; removal, leaving, a role
  change, lapsed advisor access and account deletion all count.
- Notes: Server: V025 (`household_key_state`, grants survive account deletion with a null user),
  `KeyRotationService` (`GET /keys/household/state`, `POST /keys/household/rotate`),
  `SharedKeyVersions` used by sync push to refuse stale seals. Core: `rotateKeysIfDue` in the
  key exchange, `shareKeysWithMembers` hands out every version in batches of 50, push retries once
  after `KEY_ROTATED`. Web: removal notice and a line beside Leave (en, ar).
