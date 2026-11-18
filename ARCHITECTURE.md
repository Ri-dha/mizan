# Mizan architecture

Mizan is personal budgeting and net worth for Iraq: a monthly allocation plan plus an asset
register (cash, gold and silver, belongings, debts), Arabic and English, usable offline. This file
is the source of truth for how it is built. Decisions behind it are in `docs/adr/`; changing any of
them means a new ADR and an edit here in the same task.

## Repositories (ADR split-1)

- `Ri-dha/mizan` (`MizanApi`): the server, compose, backup and observability, the runbook.
- `Ri-dha/MizanApp` (`MizanApp`): the web app, the shared test vectors, the manual, the web
  image's Caddy config, and from T-mobile-app the shared core and the phone app.
- This file is kept in both; each repository details its own side.

## Shape

```
web/  React 19 PWA (offline-first)  ──HTTPS──▶  Caddy  ──▶  api/  Spring Boot 4.1, Java 25
      Dexie (IndexedDB) + outbox                         │
                                                          ├──▶ PostgreSQL 16 (RLS)
                                                          ├──▶ SeaweedFS (S3) receipts, backups
                                                          └──▶ price feeds, Web Push services
shared/test-vectors/  JSON cases every client's domain maths must pass
infra/  caddy, backup (pg_dump → SeaweedFS), observability (Elastic, Logstash, Kibana)
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
- When someone's access to a shared key ends (removal, leaving, a role that may not hold it,
  lapsed advisor access, account deletion), the next sync of a remaining key holder's device
  rotates it (ADR e2e-4): the server computes that a rotation is due from the grants of the
  current version (`KeyRotationService`, `household_key_state`), accepts exactly one new version
  wrapped for every remaining member, deletes the leaver's grants, and refuses pushes sealed with
  any other version (`KEY_ROTATED`). Old records keep their version; newcomers get every version.
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
- Someone without their recovery code can start over (ADR e2e-3, `/keys/me/reset`): new keys,
  and the server removes what only the old keys could open (their private records; a household's
  shared records too when they were its last key holder). Their other devices notice the new
  public key, wipe their copy and ask for the password.
- On the device, keys are sealed under a non-extractable IndexedDB key. Rows in IndexedDB are
  still plain (`T-local-encryption`).

## Data and sync (ADR foundation-2, sync-1)

- Every record has a client-generated UUID and lives first in the device's IndexedDB.
- A write stamps each changed field with a hybrid logical clock (`<ms>:<counter>:<node>`) and adds
  an outbox op (`web/src/db/write.ts`). The engine (`web/src/sync/engine.ts`) pushes the outbox,
  pulls the server's change log after a cursor, and merges per field: the newer stamp wins, the
  loser is recorded as a conflict and shown on the Sync screen. A field arriving with the clock
  already stored is a retry and changes nothing (ADR sync-2); a nightly job keeps only the newest
  `sync_log` entry per record and a month of push receipts. Synced records are not audited one by one.
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

- Quotes, history and changes are the same for everyone and asked for on every sync, so they are
  cached in memory (`MarketCache`, cleared after each stored price, 60 s expiry) outside the
  transaction, and their GETs carry an ETag so an unchanged answer is a 304.

- Instruments: `XAU`, `XAG` (USD/oz), `USDIQD_OFFICIAL`, `USDIQD_PARALLEL`, `XAU_LOCAL`,
  `XAG_LOCAL` (dealer ask, IQD per gram of pure metal), `XAU_LOCAL_BID`, `XAG_LOCAL_BID`.
- Feeds are `PriceFeed` beans tried in `@Order`. IQWealth (iraqsm.com) answers both dollar rates
  first when `MIZAN_IQWEALTH_API_KEY` is set. Its free quota is 100 requests a day for everyone,
  so `IqWealthPoller` calls it on a Baghdad-time schedule (59 a day, never past a 40-request
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

In MizanApp (ADR split-1); its `ARCHITECTURE.md` describes the client side.

## Observability (ADR obs-1)

The API logs one Logstash-shaped JSON object per line (`logback-spring.xml`: readable console plus
JSON file in dev, JSON stdout in prod, plain in test) with `correlationId`, `traceId`, `spanId`,
`userId`, `httpMethod`, `httpPath`, and an access line per request: INFO for errors, client
errors and requests over 1 s, DEBUG otherwise (ADR obs-3). Logstash ships API lines to
`mizan-logs-*` and other containers to `containers-logs-*`; Micrometer pushes metrics to
`mizan-metrics-*`; Kibana has a prebuilt "Mizan overview" dashboard. Every index is deleted
after a set age through lifecycle policies (ADR obs-2: logs 14 days, container logs and metrics
7), and Docker caps each container's output at 30 MB.

## Deployment

One host with Docker Compose profiles: default (`postgres`, `seaweedfs`), `full` (plus the API),
`prod` (plus Caddy with automatic TLS, nightly `pg_dump` to SeaweedFS, observability),
`observability`, `containers` (Metricbeat). Flyway migrates on API start. Operations are in
`docs/RUNBOOK.md`.

- Object storage is SeaweedFS (ADR storage-1), one `weed server -s3` container, reached only
  through the S3 API, so any S3 store can replace it. Receipt photos are shrunk to grey 1200 px
  before encryption and live under `receipts/<household>/<id>`; `ReceiptPhotoExpiry` deletes
  them 30 days after upload and devices drop their copies on the same rule (ADR storage-2). The
  app says so where a photo is added. Device backups keep `<household>/<id>`.
  Links are signed for `MIZAN_STORAGE_PUBLIC_ENDPOINT` (ADR storage-4), normally the site itself,
  which Caddy forwards to storage under `/mizan-attachments/*`; the API keeps the internal address
  for its own calls, and storage listens on 127.0.0.1 only.
- Scheduled jobs run through `common/jobs/JobLock` (ADR csp-and-job-lock-1): a row per job in
  `scheduled_job_lock`, taken by an upsert that succeeds only when the previous lease has passed,
  judged by the database clock. A job holds it at least a few minutes, so an instance whose
  schedule fires a moment later skips. Several API instances are safe.
- Caddy's security headers, the Content-Security-Policy among them, live in
  `infra/caddy/security-headers.caddy` (ADR csp-and-job-lock-2); `vite preview` serves the same
  file.
