# Handover — 2026-11-19 — Rotate shared keys when access ends

## Completed (uncommitted)
- MizanApi `feat/key-rotation`: ADR e2e-4; V025; `KeyStateRepository`, `KeyRotationService`,
  `KeyHolding`; controller endpoints; `SharedKeyVersions` and the seal check in `SyncService`;
  `VaultFields.sealOf`; grant start and reset cleanup; errors `KEY_ROTATED`,
  `KEY_ROTATION_CONFLICT`, `KEY_ROTATION_REFUSED`; `KeyRotationIntegrationTest`; a third test account.
- MizanApp `feat/key-rotation` (worktree ../.wt-key-rotation): keyring rotation and sharing,
  engine push recovery, regenerated API types, tests, household notes (en, ar), ADR copy, docs.

## In flight
- Nothing half-done. Awaiting the owner's word to commit and merge in both repos.

## Next steps
- After merging, the other session's MizanApp checkout (feat/mobile-lock) should rebase on develop.
- The price-refresh race (duplicate first quote) is still open.

## Blockers
- None.

## Gotchas hit this session
- The phone session works in the MizanApp checkout; this work used a worktree to stay clear.
- The shared dev database on 5432 is used by an API from IntelliJ; tests here used a throwaway
  Postgres on 55432 so V025 was not applied to it.
- Background tabs lock themselves; unlock before driving them.
