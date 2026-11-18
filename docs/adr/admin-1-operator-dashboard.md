# ADR admin-1 — An operator dashboard that cannot read records

- Date: 2026-11-18
- Status: accepted
- Task: T-admin-dashboard

## Context
Every key and setting lived in the environment and needed a restart; there was no role above a
household. The owner wants people who run the service to manage keys and settings and to help
users. ADR e2e-1 means the server cannot read records, and this must stay true for operators.

## Decision
- **Role**: `app_user.platform_role` (`NONE`, `OPERATOR`), separate from household roles. It is
  read from the database on every `/api/v1/admin` call, so revoking it is immediate, and it is
  not put in the access token. The first operator comes from `MIZAN_BOOTSTRAP_OPERATOR`; later
  ones are granted by operators; the last one cannot be removed. `/me` says whether the caller is
  one so the app can show the link.
- **Step-up**: reading needs the role; every change also needs the password again (the
  device-derived auth key), which opens a window stored on the account (`operator_elevated_until`,
  10 minutes) so it holds across instances.
- **Settings**: only the keys in `SettingKey` are editable, each typed and validated. A row in
  `platform_setting` overrides the environment default; `SettingsService` re-reads the table at
  most every 15 seconds, so a change reaches every instance without a restart. Secrets are
  AES-GCM encrypted under `MIZAN_SETTINGS_KEY` (environment only, AAD = the setting's name), shown
  masked, and audited masked. Database, signing keys, pepper, storage credentials and the
  settings key itself stay environment-only. Feeds read their keys and switches on each use.
- **Support**: suspend (sessions revoked, sign-in and refresh refused with `ACCOUNT_SUSPENDED`),
  restore, sign out everywhere, cancel a deletion request. Each needs a reason where it changes
  someone else's account, and is audited.
- **Prices**: a correction is stored like any price from source `correction`; a wrong tick can be
  deleted (ticks refuse edits, ADR prices-2). Both are audited with the reason.
- **Announcements**: bilingual, optional end time, shown to every signed-in user as a banner they
  can close; retracting hides them at once.
- **Feed health** is kept in memory per instance since start-up.

## Consequences
- Operators cannot see or export records, and there is still no way to recover a lost recovery
  code (T-e2e-key-reset).
- Cron schedules, sign-in limits and grace periods are not editable yet; adding a setting means
  adding a `SettingKey` and reading it through `SettingsService`.
- Push announcements, TOTP for operators and multi-instance feed health are follow-ups.
