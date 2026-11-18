# Handover — 2026-11-18 — End-to-end encryption of every record

## Completed
- ADR e2e-1 implemented and ADR e2e-2 written for the decisions made while building it.
- Server: `V021__end_to_end_encryption.sql`, `iq.mizan.vault`, auth keys, sealed sync, schedule
  dispatch; server-side calculators and the Java domain removed. Tests rewritten (household,
  roles, sync, auth) plus `VaultIntegrationTest` and `NotificationScheduleIntegrationTest`.
- Web: `src/vault/*`, sealed push and pull, `src/sync/duties.ts`, `src/db/householdFigure.ts`,
  `features/vault/VaultScreens.tsx`, net worth and advisor pages moved off removed endpoints,
  household switch keeps PIN and account keys, regenerated `src/api/generated.d.ts`.
- Docs: ARCHITECTURE.md, AGENTS.md, README endpoints, manual pages 14 and 18 in en and ar.
- Nothing committed; everything is on branch docs/project-knowledge with the docs work.

## In flight
- T-e2e-encryption: in review. T-project-docs is still waiting for its commit too; consider
  committing the two on separate branches (`docs/project-knowledge`, `feat/e2e-encryption`).

## Next steps
- Run it against the real dev database only after a backup: V021 drops foreign keys between the
  legacy tables, and the first owner sign-in migrates and deletes the plain rows.
- T-e2e-key-reset before real users, then T-e2e-key-rotation.
- Remove the `legacyPassword` fallback once no LEGACY accounts remain.

## Blockers
- None. Port 5432 is still held by tariffcalc; the browser test used a throwaway Postgres on 55432.

## Later the same session
- T-iqwealth-fx: the IQWealth dollar feed with a scheduled, budgeted poll (in review, uncommitted).
  A free test key is in the gitignored `.env` as `MIZAN_IQWEALTH_API_KEY`.
- The API suite is 65 tests (59 before the feed). An earlier "168" counted stale reports.

- T-shadcn-ui: the web app moved to standard shadcn/ui with device light and dark (in review,
  uncommitted). Plans written for T-price-history and T-admin-dashboard, in that order.

- T-price-history: every price change kept, scheduled polling, Prices screen and Home card
  (in review, uncommitted; ADR prices-2). Next: T-admin-dashboard.

- T-admin-dashboard: operator dashboard (in review, uncommitted; ADR admin-1).
- A background session committed `06c078b` (lock reset on account change) straight onto
  docs/project-knowledge in this checkout; it took only its own lines.

## Gotchas hit this session
- Joining a household used the full local wipe, which deleted the account keys and the PIN. It
  now uses `clearHouseholdData()`.
- Sync ran before the vault opened and never retried; the engine now syncs on `onVaultReady`.
- A new household auto-closed the month before it existed; auto-close now starts at the first plan.
- Unrelated: concurrent `POST /market/refresh` can 500 on `price_quote_pkey` (flagged as a task).
