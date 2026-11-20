# Mizan

Personal budgeting and net worth for the Iraqi market: a monthly allocation plan plus an asset
register (cash, gold, belongings) that together answer "what am I worth, and why did that change?".
Requirements live in the BRD; the build order is in [PLAN.md](PLAN.md).

This repository holds the server (`api/`), Docker Compose, backups, observability and the
runbook. The web app, the shared test vectors, the user manual and (from T-mobile-app) the phone
app live in [Ri-dha/MizanApp](https://github.com/Ri-dha/MizanApp), cloned next to this one as
`MizanApp` (ADR split-1).

## Tech stack

**Backend (`api/`)**

| Concern | Choice |
|---|---|
| Language and runtime | Java 25, virtual threads |
| Framework | Spring Boot 4.1 (Spring MVC, Spring Security, Spring Data JPA, Actuator), Jackson 3 |
| Database | PostgreSQL 16 with row-level security per household and per private record; Flyway owns the schema; `JdbcClient` for the ledger maths |
| Object storage | SeaweedFS through the AWS S3 SDK, presigned upload and download URLs; receipts are shrunk to grey 1200 px, encrypted on the device before upload, and deleted after 30 days |
| Auth | JWT access tokens (jjwt), rotating refresh tokens in an httpOnly cookie, Argon2id hashes of a device-derived auth key, never the password (BouncyCastle); records end-to-end encrypted on the device (WebCrypto), Bucket4j rate limits |
| Sync | Client-generated UUIDs, hybrid logical clocks, per-field last-write-wins, an outbox on the device and a change log on the server |
| Notifications | Web Push with VAPID on the JDK crypto stack |
| Market data | Baghdad Bullion House (default), gold-api.com, open.er-api.com, metals.dev and GoldAPI when keyed; a configurable JSON dealer feed |
| Mapping and boilerplate | MapStruct, Lombok |
| API docs | springdoc OpenAPI; the web client's types are generated from it |
| Observability | Structured Logstash-style JSON logs, Micrometer Tracing (Brave) trace ids, Micrometer metrics to Elasticsearch |
| Tests | JUnit 5, Testcontainers PostgreSQL, jqwik property tests |

The web app's stack is in MizanApp's README.

**Infrastructure**

| Concern | Choice |
|---|---|
| Deployment | Docker Compose: PostgreSQL 16, SeaweedFS, the API, Caddy (PWA, TLS, `/api` proxy), nightly `pg_dump` to SeaweedFS |
| Logs | Elasticsearch and Filebeat 9.3; Kibana on demand with a prebuilt dashboard |
| Metrics and alerts | VictoriaMetrics, vmalert and Alertmanager (Telegram), Grafana dashboard, Uptime Kuma |
| CI | GitHub Actions: Maven tests on Java 25 |

## Run it locally

Prerequisites: Java 25, Docker.

```bash
cd api && ./mvnw spring-boot:run
```

That starts Postgres and SeaweedFS from `docker-compose.yml` automatically, migrates the schema, and
serves the API. Verification and password-reset codes are printed to the API log in the dev
profile (look for `[DEV ONLY]`) unless `MIZAN_MAIL_HOST` is set, in which case they are emailed
(ADR auth-email-1).

For the web app, see MizanApp's README; its dev server proxies `/api` to this API.

To run everything in Docker, including the API image:

```bash
docker compose --profile full up --build
```

To deploy, use the `prod` profile (with MizanApp checked out next to this repository, or
`MIZAN_APP_DIR` pointing at it): Caddy serves the built PWA with automatic TLS, proxies `/api`,
and a backup container dumps Postgres nightly into SeaweedFS. Steps, restore, secret rotation and the
scheduled jobs are in [docs/RUNBOOK.md](docs/RUNBOOK.md).

## URLs and ports

| What | URL | Notes |
|---|---|---|
| Kibana | http://localhost:5601 | `docker compose --profile observability up -d`; dashboard "Mizan overview" |
| Elasticsearch | http://localhost:9200 | Indices `mizan-logs-*` (API), `containers-logs-*` (everything else) |
| Grafana | http://localhost:3000 | Metrics dashboard "Mizan metrics", logs, alerts; user `admin`, password `MIZAN_GRAFANA_ADMIN_PASSWORD` |
| Uptime Kuma | http://localhost:3001 | Up/down checks and the backup check-in; account created on first visit |
| VictoriaMetrics | http://localhost:8428 | Metrics store; `/vmui` to query |
| API | http://localhost:8080 | REST under `/api/v1` |
| Swagger UI | http://localhost:8080/swagger-ui.html | Enabled in the `dev` profile; off elsewhere unless `SWAGGER_UI_ENABLED=true` |
| OpenAPI document | http://localhost:8080/v3/api-docs | What `npm run api:types` reads |
| Health | http://localhost:8080/actuator/health | Liveness and readiness groups |
| Postgres | `jdbc:postgresql://localhost:5432/mizan` | user `mizan`, password `mizan` (dev only) |
| SeaweedFS S3 endpoint | http://localhost:8333 | Bucket `mizan-attachments`, created by the API on startup; receipts under `receipts/` |

Every credential above is a dev default from `.env.example` and `application.yml`. Deployed
environments override them with `MIZAN_*` and `DB_*` environment variables.

### Logs, statistics and performance (Kibana)

```bash
docker compose --profile observability --profile kibana up -d
```

Elasticsearch and Filebeat start with `observability` (and with `prod`); Kibana
(http://localhost:5601, bound to localhost only) only with the `kibana` profile, so it runs while
someone is looking. A one-shot `kibana-setup` container creates the data views and imports the
**Mizan overview** dashboard: requests per status, p95 and average latency, slowest paths, log
lines by level, errors and warnings by logger, and most frequent messages. Metrics live in
VictoriaMetrics and the **Mizan metrics** dashboard in Grafana (ADR obs-5).

The API writes one JSON object per log line in Logstash shape, with `correlationId`,
`httpMethod`, `httpPath`, `userId`, `traceId` and `spanId` as top-level fields and one access
line per request (`logger_name: iq.mizan.http`) carrying `httpStatus` and `durationMs`. In
Docker, Filebeat reads container output; on a developer machine it tails `api/logs/mizan.json`,
which the API writes as well (`api/logs/`, ignored by git). Log files idle for a day are skipped; other containers' lines land in `containers-logs-*` so their fields never
collide with the API's. Appenders are in `api/src/main/resources/logback-spring.xml`: the `dev`
profile logs readably to the console and as JSON to the file, `prod` logs JSON to stdout only,
`test` plainly to the console.
JVM, HTTP, Hikari, cache and IQWealth metrics are served at `/actuator/prometheus` (no login,
reachable only from this machine and the Docker network) and read by VictoriaMetrics. For an API
run outside Docker set `MIZAN_API_METRICS_TARGET=host.docker.internal:8080`.

### Price feeds

Live without any key. Baghdad Bullion House (`prices.baghdadbullionhouse.com`) is the default
local source: its ask per gram of pure metal feeds `XAU_LOCAL`/`XAG_LOCAL`, its bid feeds
`XAU_LOCAL_BID`/`XAG_LOCAL_BID`, and its product list is kept for `GET /api/v1/market/catalogue`.
gold-api.com supplies world spot in dollars and open.er-api.com the official dollar rate. Feeds
are called only on request (`POST /api/v1/market/refresh`, cooldown 30 s), which the app does
once on load and when the user presses Refresh; set `MIZAN_MARKET_AUTO_REFRESH=true` for a timed
refresh as well. The parallel rate has no public feed and stays a configured value or a user override.

Locally the API serves fixed stub prices (see `application-dev.yml`). To use real providers set
any of `MIZAN_METALS_DEV_API_KEY`, `MIZAN_GOLD_API_KEY`, `MIZAN_OPEN_EXCHANGE_RATES_APP_ID`,
or `MIZAN_EXCHANGE_RATE_API_URL=https://open.er-api.com` (keyless). Providers are tried in that
order every hour. The Baghdad market dollar (and the Central Bank rate) comes from IQWealth when
`MIZAN_IQWEALTH_API_KEY` is set: polled on a schedule within its free 100-requests-a-day quota,
personal and non-commercial use only, credited in the app. Without it, the market dollar comes
from `MIZAN_PARALLEL_RATE_IQD_MICROS` (1470 IQD/USD = `1470000000`) or a price the user enters.

### API endpoints (v1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | none | Create an account with an email address, an auth key derived on the device and the wrapped account keys; returns tokens and creates the household |
| POST | `/api/v1/auth/login` | none | Sign in with the auth key (accounts from before encryption send `legacyPassword` once and are upgraded); five failures lock the account for 15 minutes |
| POST | `/api/v1/auth/refresh` | cookie or body | Rotate the refresh token; replaying an old one revokes every session |
| POST | `/api/v1/auth/logout` | cookie or body | Revoke the refresh token |
| POST | `/api/v1/auth/verify` | bearer | Confirm the email address with the delivered code |
| POST | `/api/v1/auth/verify/resend` | bearer | Send a fresh code (60 s cooldown) |
| POST | `/api/v1/auth/password-reset/request` | none | Send a reset code; always 204 |
| POST | `/api/v1/auth/password-reset/confirm` | none | Set a new auth key; ends all sessions. The keys then need the recovery code |
| GET | `/api/v1/me` | bearer | The signed-in account and its household |
| GET | `/api/v1/households/current` | bearer | The caller's household |
| PATCH | `/api/v1/households/current` | bearer, `PLAN_EDIT` | Rename or change the month start day |
| POST | `/api/v1/sync/devices` | bearer | Register the calling device |
| POST | `/api/v1/sync/push` | bearer, `RECORD_WRITE` | Push sealed changes; plain values are refused; per-field last-write-wins |
| GET | `/api/v1/sync/pull?deviceId&since&limit` | bearer | Pull the change feed after a sequence number; `encrypted: false` marks rows from before encryption |
| GET | `/api/v1/sync/conflicts?deviceId` | bearer | Edits from this device that lost to newer values |
| POST | `/api/v1/attachments/{id}/upload-url` | bearer, `RECORD_WRITE` | Presigned PUT for the encrypted bytes of a synced attachment row |
| GET | `/api/v1/attachments/{id}/download-url` | bearer | Presigned GET for the same |
| GET | `/api/v1/market/quotes` | bearer | Latest XAU, XAG and USD→IQD quotes with source, time and stale flag |
| GET | `/api/v1/market/history?instrument&from&to&interval` | bearer | Open, high, low and close per day, week (from Saturday) or month |
| GET | `/api/v1/market/changes?instrument&from&before&limit` | bearer | Every change of a price, newest first, with the price it replaced |
| GET | `/api/v1/households` | bearer | Every household the caller belongs to and which is current |
| POST | `/api/v1/households/{id}/switch` | bearer | Make another household current; refresh the session afterwards |
| GET | `/api/v1/households/current/members` | bearer, `HOUSEHOLD_VIEW` | Active members with roles |
| PATCH | `/api/v1/households/current/members/{userId}` | bearer, `MEMBERS_MANAGE` | Change a member's role |
| DELETE | `/api/v1/households/current/members/{userId}` | bearer, `MEMBERS_MANAGE` | Remove a member |
| POST | `/api/v1/households/current/leave` | bearer | Leave; refused for the owner or the only household |
| POST | `/api/v1/households/current/transfer-ownership` | bearer, `HOUSEHOLD_DELETE` | Hand ownership to a member |
| POST | `/api/v1/households/current/invitations` | bearer, `MEMBERS_MANAGE` | Create an invitation; returns the token once |
| GET | `/api/v1/households/current/invitations` | bearer, `MEMBERS_MANAGE` | Open invitations |
| DELETE | `/api/v1/households/current/invitations/{id}` | bearer, `MEMBERS_MANAGE` | Revoke |
| POST/DELETE | `/api/v1/households/current/deletion-request` | bearer, `HOUSEHOLD_DELETE` | Request (with the auth key) or cancel household deletion; purged after 7 days |
| GET | `/api/v1/invitations/{token}` | public | Preview an invitation |
| POST | `/api/v1/invitations/{token}/accept` | bearer | Join the household behind the link |
| POST | `/api/v1/market/refresh` | bearer | Fetch fresh prices from the feeds now (cooldown 30 s) and return the quotes |
| GET | `/api/v1/market/catalogue` | bearer | The dealer's bars and coins with bid and ask, as last fetched |
| GET | `/api/v1/market/quotes` (instruments `XAU_LOCAL`, `XAG_LOCAL`) | bearer | The local market's quote per gram of pure metal, when a dealer feed is configured |
| GET | `/api/v1/notifications/vapid-key` | bearer | The server's VAPID public key for subscribing a browser |
| GET/POST/DELETE | `/api/v1/notifications/subscriptions` | bearer | This account's push subscriptions; register or remove one |
| POST | `/api/v1/notifications/test` | bearer | Send a test notification to every subscribed device |
| PUT | `/api/v1/notifications/schedule` | bearer | Replace this device's reminder schedule: kinds and times only, no names or amounts |
| GET/PUT/PATCH | `/api/v1/keys/me` | bearer | The caller's wrapped account keys and grants; store them once; re-wrap after a reset or a new recovery code |
| GET/POST | `/api/v1/keys/me/reset-preview`, `/api/v1/keys/me/reset` | bearer | What starting over without a recovery code removes; replace the keys (confirm word RESET) |
| GET | `/api/v1/keys/household/members` | bearer, `HOUSEHOLD_VIEW` | Members' public keys and which key versions they hold |
| POST | `/api/v1/keys/household/grants` | bearer, `HOUSEHOLD_VIEW` | Hand household or report keys, wrapped to members' public keys |
| GET | `/api/v1/vault/status` | bearer | Whether plain rows from before encryption remain |
| POST | `/api/v1/vault/migration/complete` | bearer, `RECORD_WRITE` | Delete the plain copies of rows the caller has re-sent sealed |
| GET | `/api/v1/announcements` | bearer | Announcements showing now |
| GET/POST | `/api/v1/admin/session` | bearer, operator | Whether changes are allowed; re-enter the password to allow them for 10 minutes |
| GET | `/api/v1/admin/overview` | bearer, operator | Counts, sizes, feed health and the IQWealth quota |
| GET/PUT/DELETE | `/api/v1/admin/settings[/{key}]` | bearer, operator | Run-time settings with defaults; secrets masked |
| POST | `/api/v1/admin/feeds/poll` | bearer, operator | Fetch the dollar (one IQWealth request) or every other feed now |
| POST/DELETE | `/api/v1/admin/prices/corrections`, `/api/v1/admin/prices/changes/{id}` | bearer, operator | Correct a price or remove a wrong change, with a reason |
| GET/POST | `/api/v1/admin/accounts`, `…/{userId}/suspend\|restore\|revoke-sessions\|cancel-deletion` | bearer, operator | Find accounts and change their state; never their records |
| GET/POST/DELETE | `/api/v1/admin/announcements[/{id}]` | bearer, operator | Publish or retract a bilingual announcement |
| GET | `/api/v1/admin/audit` | bearer, operator | Search the audit log |
| GET/POST/DELETE | `/api/v1/admin/operators[/{userId}]` | bearer, operator | Who runs the service; the last operator stays |
| POST | `/api/v1/me/deletion-request` | bearer | Request account deletion; purged after 30 days |
| DELETE | `/api/v1/me/deletion-request` | bearer | Withdraw the request |

The access token goes in `Authorization: Bearer <token>` and lives 15 minutes. The refresh token
is a 30-day httpOnly cookie scoped to `/api/v1/auth`, and is also returned in the body for
clients without a cookie jar. Errors are RFC 7807 `application/problem+json`; branch on `code`.

## Tests

```bash
cd api && ./mvnw test
```

Integration tests run against a real Postgres in Testcontainers, so Docker must be running.


## Layout

- `api/src/main/java/iq/mizan/<module>` — one package per module (`auth`, `household`, `sync`, `asset`, …) with
  `controller`, `dto`, `entity`, `mapper`, `repository`, `service`. `common` holds security, tenancy,
  audit, errors and storage. `vault` holds the key directory and migration. The server keeps no
  money maths: it cannot read the values (ADR e2e-1).
- `api/src/main/resources/db/migration` — Flyway migrations. Every syncable table is created with
  `call make_syncable('<table>')`, which adds the change-feed trigger and the row-level security policy.

Project knowledge: [AGENTS.md](AGENTS.md) (how to work here), [ARCHITECTURE.md](ARCHITECTURE.md),
and `docs/` (ADRs, tasks, status, learnings, handovers, runbook).
