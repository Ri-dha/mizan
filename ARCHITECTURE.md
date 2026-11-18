# Mizan architecture

Mizan is personal budgeting and net worth for Iraq: a monthly allocation plan plus an asset
register (cash, gold and silver, belongings, debts), Arabic and English, usable offline. This file
is the source of truth for how it is built. Decisions behind it are in `docs/adr/`; changing any of
them means a new ADR and an edit here in the same task.

## Shape

```
web/  React 19 PWA (offline-first)  ──HTTPS──▶  Caddy  ──▶  api/  Spring Boot 4.1, Java 25
      Dexie (IndexedDB) + outbox                         │
                                                          ├──▶ PostgreSQL 16 (RLS)
                                                          ├──▶ MinIO (S3) receipts, backups
                                                          └──▶ price feeds, Web Push services
shared/test-vectors/  JSON cases every client's domain maths must pass
infra/  caddy, backup (pg_dump → MinIO), observability (Elastic, Logstash, Kibana)
docs/manual/  one Markdown source → in-app /help and static /docs
```

## End-to-end encryption (ADR e2e-1, e2e-2)

- The server never sees a password, an amount, a name or a note. The device derives an auth key
  and a wrapping key from the password (PBKDF2 600k, HKDF; `web/src/vault/crypto.ts`). Only the
  auth key is sent, and the server hashes it with Argon2id.
- Each user has a user key (wrapped by the password key and by a one-time recovery code) and an
  ECDH P-256 pair. Each household has a household key and a report key, versioned and granted by
  wrapping them to members' public keys (`/api/v1/keys`, `web/src/vault/keyring.ts`). Members hold
  both, viewers the household key, advisors only the report key, dependents neither.
- Every synced field except `visibility` and `deletedAt` is sealed on the device as
  `e1.<h|u|r>.<version>.<base64url>` (AES-GCM, padded to 32 bytes, AAD = table, record id, field;
  `web/src/vault/records.ts`). The server stores records in `vault_record` and rejects plain
  values (`VaultFields`).
- What the server used to compute now happens on members' devices (`web/src/sync/duties.ts`,
  `web/src/db/householdFigure.ts`): month close and snapshots, the household figure (each member
  publishes a sealed `member_contribution` of their private holdings), the advisor's
  `annual_report` (report key), the default plan, and the reminder schedule. For reminders the
  server is told only a kind and a time, and sends generic text.
- Rows from before encryption stay in their old plain tables and are pulled with
  `encrypted: false`. Each device re-sends its rows sealed, then `/vault/migration/complete`
  deletes the plain copies.
- On the device, keys are sealed under a non-extractable IndexedDB key. Rows in IndexedDB are
  still plain (`T-local-encryption`).

## Data and sync (ADR foundation-2, sync-1)

- Every record has a client-generated UUID and lives first in the device's IndexedDB.
- A write stamps each changed field with a hybrid logical clock (`<ms>:<counter>:<node>`) and adds
  an outbox op (`web/src/db/write.ts`). The engine (`web/src/sync/engine.ts`) pushes the outbox,
  pulls the server's change log after a cursor, and merges per field: the newer stamp wins, the
  loser is recorded as a conflict and shown on the Sync screen.
- The server (`api/.../sync`) applies pushes idempotently by op id, merges sealed fields by their
  clocks, writes `sync_log` through a trigger on `vault_record`, and refuses any change to a
  stored append-only record (net worth snapshots). Records waiting for a key the device has not
  been granted yet are held in `sealedInbox` until it is.
- Money is BIGINT minor units plus a currency. Exchange rates are micros **per minor unit** of the
  source currency (ADR rates-1); `rateToMicros`/`microsToRate` in `web/src/db/income.ts` are the
  only conversion between what people type and what is stored.

## Tenancy and privacy (ADR tenancy-1, households-1)

- Every household table is `FORCE ROW LEVEL SECURITY`. Each request transaction switches to the
  non-owner role `mizan_app` and sets `app.user_id` and `app.household_id`
  (`TenantTransactionBinder`). Policies allow shared rows of the household and private rows of
  their owner only.
- `app.system = on` lets server jobs (purge, migration) see every row; `app.restricted = on`
  (sessions without `HOUSEHOLD_VIEW`: dependents and advisors) hides shared rows, and
  `app.reports = on` (`REPORTS_VIEW`) lets advisors read the shared `annual_report` rows only.
- A user can belong to several households; the current membership decides the JWT's household and
  permissions. Roles map to permissions on `HouseholdRole`; code checks permissions only.
- Auth: Argon2id over the device-derived auth key (never the password), 15-minute JWT access tokens, rotating refresh tokens in an httpOnly
  cookie (or request body for non-browser clients), replay of a rotated token revokes all sessions.

## Operators (ADR admin-1)

- An operator role on the account (`platform_role`), separate from households and checked in the
  database on every `/api/v1/admin` call. Changes need the password again within 10 minutes.
- Run-time settings: an allowlist (`platform/service/SettingKey`) stored in `platform_setting`,
  secrets AES-GCM encrypted under `MIZAN_SETTINGS_KEY`; `SettingsService` serves the database value
  or the environment default and re-reads within 15 seconds. Feeds read keys through
  `MarketSettings` on each use.
- The dashboard (`web/src/features/admin`) shows counts, feed health and quota, settings, price
  corrections, account support (suspend, restore, sign out, cancel deletion), bilingual
  announcements, the audit log and operators. It cannot read records (ADR e2e-1).

## Device security (ADR lock-1)

- A random data key is wrapped by a PBKDF2 key from the PIN and, optionally, by a WebAuthn PRF
  secret (biometrics); it guards the screen. Each receipt has its own AES-GCM key carried sealed in
  its attachment row (`blobKey`), so every member who can open the row can open the file; older
  receipts use the device data key. Rows in IndexedDB are not encrypted (`T-local-encryption`).

## Domain maths

Pure code with no framework in `web/src/domain`; the server no longer calculates (ADR e2e-1). Each
module has a vector file in `shared/test-vectors/` that every client (web now, Flutter later) must
pass: money split (largest remainder), month window, planned
income, month figures (fixed buckets, carry-over, transfers), debt payoff, debt strategy,
goal projection, metal valuation (BigInt, four half-up rounding points, buy-back spread),
lot disposal (FIFO, specific, weighted average), depreciation, net worth. jqwik and fast-check
property tests prove aggregates reconcile.

## Market data (ADR prices-1)

- Instruments: `XAU`, `XAG` (USD/oz), `USDIQD_OFFICIAL`, `USDIQD_PARALLEL`, `XAU_LOCAL`,
  `XAG_LOCAL` (dealer ask, IQD per gram of pure metal), `XAU_LOCAL_BID`, `XAG_LOCAL_BID`.
- Feeds are `PriceFeed` beans tried in `@Order`. IQWealth (iraqsm.com) answers both dollar rates
  first when `MIZAN_IQWEALTH_API_KEY` is set. Its free quota is 100 requests a day for everyone,
  so `IqWealthPoller` calls it on a Baghdad-time schedule (about 45 a day, never past a 40-request
  reserve, paused until midnight on a 429, off on 401/403), and the feed answers lookups from the
  last poll. The web shows "Data: IQWealth" wherever its rate appears (`SourceCredit`), as the free
  licence requires; a public deployment needs their Business plan. Then Baghdad Bullion House (default local source, also
  stores the dealer catalogue), configurable JSON dealer feed, metals.dev and GoldAPI when keyed,
  gold-api.com and open.er-api.com keyless, a stub in dev and test. A feed with a blank key or URL
  declines.
- Prices are fetched only on request: `POST /api/v1/market/refresh` (30 s cooldown), which the app
  calls on load and from the Refresh button. A timed refresh exists behind
  `MIZAN_MARKET_AUTO_REFRESH`. Without an IQWealth key, the market dollar comes from
  `MIZAN_PARALLEL_RATE_IQD_MICROS` or a user override.
- Every change of a price is kept in `price_tick` (append-only, written only when the price moves),
  and `price_history` keeps each day's open, high, low and close (ADR prices-2). `MarketPoller`
  refreshes all feeds every 30 minutes in Baghdad hours. `/market/history` rolls up by day, week
  (from Saturday) or month and `/market/changes` pages the ticks; both are open to every signed-in
  role. The web Prices screen (`features/prices`) charts them, cached on the device.
- Valuation resolves: user override, else local quote (bid when valuing at buy-back) when the
  household's price source is LOCAL, else spot × the chosen dollar rate × purity × premium.

## Server modules (`api/src/main/java/iq/mizan`)

`auth`, `household`, `sync`, `plan`, `income`, `expense`, `transaction`, `attachment`, `debt`,
`goal`, `asset`, `metal`, `market`, `networth` (sync tables for snapshots, month closes,
contributions and annual reports), `vault` (key directory, grants, migration), `notification`
(Web Push with VAPID, dispatch of device-uploaded schedules, public metal-move alerts), `platform`
(run-time settings, announcements), `admin` (the operator API), `common`
(security, tenancy, audit, errors, storage, web filters). Modules do not import each other's
entities or repositories.

## Web (`web/src`)

`features/*` screens, `db/` the only writer to synced tables, `sync/` engine and device duties,
`vault/` keys and field sealing, `lock/` PIN and WebAuthn, `attachments/` encrypted receipts, `market/` quotes, history and resolution, `ocr/`
Tesseract on device, `tours/` react-joyride, `help/` bundled manual, `pwa/` install and service
worker, `i18n/` en and ar with RTL. UI is standard shadcn/ui (Radix, Tailwind v4)
vendored in `components/ui`, themed by the tokens in `index.css` (ADR ui-1). Light or dark follows
the device unless Settings says otherwise; `public/theme-boot.js` sets it before first paint.

## Observability (ADR obs-1)

The API logs one Logstash-shaped JSON object per line (`logback-spring.xml`: readable console plus
JSON file in dev, JSON stdout in prod, plain in test) with `correlationId`, `traceId`, `spanId`,
`userId`, `httpMethod`, `httpPath`, and one access line per request. Logstash ships API lines to
`mizan-logs-*` and other containers to `containers-logs-*`; Micrometer pushes metrics to
`mizan-metrics-*`; Kibana has a prebuilt "Mizan overview" dashboard.

## Deployment

One host with Docker Compose profiles: default (`postgres`, `minio`), `full` (plus the API),
`prod` (plus Caddy with automatic TLS, nightly `pg_dump` to MinIO, observability),
`observability`, `containers` (Metricbeat). Flyway migrates on API start. Operations are in
`docs/RUNBOOK.md`.
