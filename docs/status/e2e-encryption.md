# Status — e2e-encryption
- Branch: docs/project-knowledge (with the docs work), uncommitted. Nothing committed for this task.
- Exists: V021, the `vault` module, auth keys, sealed sync, key grants, migration endpoint and
  client migration, device-side month close, household figure, annual report, default plan and
  reminder schedule, recovery and unlock screens, per-file receipt keys, generated API types.
- Removed: server networth/report/plan-seeding/notification-rule code and the Java `domain`
  package with its tests.
- Mocked or stubbed: nothing. Advisor screen reads the device-built report only.
- In flight: waiting for review and a commit request.
- Blockers: none. Open gaps are proposed tasks: T-e2e-key-rotation, T-e2e-key-reset.
- Validated 2026-11-18: API `./mvnw -B test` 59 green (an earlier count of 168 included stale reports of deleted tests); web `vitest` 164 green, `tsc -b` clean,
  oxlint no new findings. Browser run against a throwaway Postgres and MinIO: register, owner
  bootstraps keys, member joins and is granted keys automatically, member sees shared rows and the
  household figure (1,750,000 with 500,000 private) but not the private row, a seeded plain legacy
  row is migrated and deleted from its plain table, password reset then recovery code reopens
  everything. Database searched for the names and amounts used: none found.
