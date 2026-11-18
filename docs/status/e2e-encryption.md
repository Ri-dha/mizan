# Status — e2e-encryption
- Branch: merged. Commit 1c2d7d2 on feat/e2e-prices-admin; main and develop at d5099dc.
- Exists: V021, the `vault` module, auth keys, sealed sync, key grants, migration endpoint and
  client migration, device-side month close, household figure, annual report, default plan and
  reminder schedule, recovery and unlock screens, per-file receipt keys.
- Removed: server networth, report, plan-seeding and notification-rule code and the Java `domain`
  package with its tests.
- Mocked or stubbed: nothing.
- In flight: nothing. The dev database was recreated empty on 2026-11-18, so there is no plain
  data left to migrate locally.
- Blockers: none. Starting over without a recovery code is T-e2e-key-reset (in review);
  key rotation after a member leaves is T-e2e-key-rotation (proposed).
- Validated 2026-11-18 on a clean checkout of d5099dc: API 77 tests in 20 suites, web 165, tsc,
  build. Browser run with two accounts, migration, reset and recovery.
