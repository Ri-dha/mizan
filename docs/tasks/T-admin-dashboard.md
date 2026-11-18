# T-admin-dashboard — An operator dashboard for keys, settings and support
- Status: in-review (2026-11-18), uncommitted on branch docs/project-knowledge
- Context: owner request 2026-11-18. Needs ADR admin-1 (operator role, settings store, what stays
  in the environment). After T-price-history (feed health and price corrections build on ticks).
- Decisions (owner, 2026-11-18): third, after T-price-history.
- Notes:
  - Today: every key and setting is an environment variable or `application.yml`, read at
    start-up; roles exist only inside households. ADR e2e-1 means an operator can never read
    anyone's records, and this task must not add a way to.
  - Server:
    1. Operator role on the account (`app_user.platform_role`: NONE, OPERATOR), granted only by
       another operator; the first one comes from `MIZAN_BOOTSTRAP_OPERATOR` at start-up. JWT
       authority `PLATFORM_OPERATOR`; every admin endpoint under `/api/v1/admin`, checked by
       permission and audited.
    2. `platform_setting` (key, typed value, secret flag, updated_by, updated_at). Secrets are
       AES-GCM encrypted under `MIZAN_SETTINGS_KEY` (environment only) and never returned, only
       masked. A database value overrides the environment default; feeds and schedules read
       through a `SettingsService` and pick changes up without a restart.
    3. Only an allowlist is editable, each with validation: feed keys and on/off, feed order,
       IQWealth crons, reserve and side, gold poll interval, manual market dollar, stale-after,
       refresh cooldown, sign-in limits, recycle-bin and deletion grace periods. The database URL,
       JWT secret, pepper, storage credentials and the settings key stay environment-only.
    4. Re-authentication before any change (password again within 5 minutes); TOTP as a follow-up.
  - Screens (`/admin`, lazy, hidden from everyone else):
    1. Feeds: status per feed (last success, last error, today's IQWealth quota), keys masked,
       on/off, order, "poll now" with a quota warning.
    2. Settings: the allowlist with defaults, current values and who changed them.
    3. Prices: correct or remove a bad tick (with a reason; corrections are logged).
    4. Accounts: counts, sign-ups, accounts still on the legacy password, households not yet
       migrated; lock or unlock, revoke sessions, cancel a deletion request. No record contents.
    5. Announcements: a banner and optional push to everyone, in English and Arabic.
    6. Audit: searchable log of sign-ins, admin actions and setting changes.
    7. Health: database size, failed jobs, push failures, link to Kibana.
  - Done (ADR admin-1): V023; operator role with bootstrap, re-authentication window, suspension;
    `platform` module (settings with encrypted secrets, announcements); `admin` module (overview,
    settings, polling, price corrections, account support, announcements, audit, operators); feeds
    read keys at run time; `/me` carries `operator`. Web: `/admin` with seven tabs, a password
    prompt that retries the change, the announcement banner, Admin in the menu for operators.
  - Fixed on the way: sync never ran again after an early exit (offline or keys not yet open) left
    its in-progress marker set; the profile now refreshes on each sync, so role changes show.
  - Not yet: editable crons, sign-in limits and grace periods; push announcements; TOTP.
  - Validated: 7 integration tests; API 77 tests in 20 suites, web 165; live run of elevation,
    a setting change and an announcement reaching Home.

