# Handover — 2026-11-18 — Job lock and Content-Security-Policy

## Completed (e15b0eb on develop)
- ADRs csp-and-job-lock-1 (lock table) and -2 (CSP).
- `api/.../db/migration/V024__scheduled_job_lock.sql`, `common/jobs/JobLock.java`, wrapped in
  AccountDeletionPurge, AttachmentObjectPurge, HouseholdDeletionPurge, RecycleBinPurge (now a
  `scheduled()` wrapper running `purge()` in a TransactionTemplate), NotificationScheduler (two
  locks), MarketPoller, MarketRefreshScheduler, IqWealthPoller (one lock name for every poll).
- `IqWealthFxFeed` falls back to the stored iraqsm.com quote while a key is configured.
- `infra/caddy/security-headers.caddy` imported by the Caddyfile; `web/Dockerfile` copies the
  whole `infra/caddy/`; compose and `.env.example` gain `MIZAN_STORAGE_ORIGIN`;
  `web/vite.config.ts` exports `productionHeaders()` for `preview.headers`.
- OCR fixes: relaxed-SIMD core copied; `src/sw.ts` keeps HTML out of the `ocr-assets` cache.
- Docs: ARCHITECTURE Deployment, RUNBOOK (scheduled jobs, CSP, OCR), task, status, learning.

## In flight
- Nothing. Committed as e15b0eb, merged into develop and pushed; main not promoted.

## Next steps
- Build and start the prod `web` image once to confirm Caddy parses the import.
- T-storage-public-endpoint.

## Blockers
- None.

## Gotchas hit this session
- Preview launch commands cannot read `Mizan/.env` (sandboxed); the API ran on dev defaults.
- A full page load locks the app; drive the UI with in-app clicks between checks.
- See learning 2026-11-18-tesseract-relaxed-simd-core.
