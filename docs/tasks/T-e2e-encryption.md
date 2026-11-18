# T-e2e-encryption — Encrypt every financial record end to end
- Status: in-review (2026-11-18), uncommitted on branch docs/project-knowledge
- Context: ADR e2e-1, ADR e2e-2. The owner chose option 3 after a database query showed plain
  balances. Follow-ups: T-e2e-key-rotation, T-e2e-key-reset, T-local-encryption.
- Notes: Server: V021 migration (`vault_record`, `user_key`, `household_key_grant`,
  `notification_schedule`), auth keys instead of passwords, `vault` module (keys, grants,
  migration), plain pushes refused, server calculators and the Java domain removed, notification
  schedule dispatch. Web: `web/src/vault` (crypto, keyring, account, records), engine seal/open
  with `sealedInbox`, `web/src/sync/duties.ts` (migration, plan seeding, contributions, month
  close, annual reports, reminder schedule), `db/householdFigure.ts`, vault screens, per-file
  receipt keys. The legacy `legacyPassword` fallback in `api/auth.ts` goes once no LEGACY accounts
  remain.
