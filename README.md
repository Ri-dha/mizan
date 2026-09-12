# Mizan

Personal budgeting and net worth for the Iraqi market: a monthly allocation plan plus an asset
register (cash, gold, belongings) that together answer "what am I worth, and why did that change?".
Requirements live in the BRD; the build order is in [PLAN.md](PLAN.md).

| Part | Stack |
|---|---|
| `api/` | Java 25, Spring Boot 4.1, PostgreSQL 16 (row-level security), Flyway, MinIO via the S3 SDK |
| `web/` | React 19 PWA, Vite, Tailwind v4, neobrutalism.dev components, Dexie (IndexedDB), offline-first sync |
| `shared/test-vectors/` | JSON cases both the Java and TypeScript domain code must satisfy |

## Run it locally

Prerequisites: Java 25, Node 24, Docker.

```bash
cd api && ./mvnw spring-boot:run
```

That starts Postgres and MinIO from `docker-compose.yml` automatically, migrates the schema, and
serves the API. Verification and password-reset codes are printed to the API log in the dev
profile (look for `[DEV ONLY]`).

```bash
cd web && npm install && npm run dev
```

The PWA proxies `/api` to the API. `npm run api:types` regenerates the typed client from the
running API's OpenAPI document; commit the result.

To run everything in Docker, including the API image:

```bash
docker compose --profile full up --build
```

## URLs and ports

| What | URL | Notes |
|---|---|---|
| Web app (dev) | http://localhost:5173 | Vite dev server with hot reload; `/api` is proxied to the API |
| Web app (built) | http://localhost:4173 | `npm run preview` after `npm run build`; the service worker only runs here or in production |
| API | http://localhost:8080 | REST under `/api/v1` |
| Swagger UI | http://localhost:8080/swagger-ui.html | Enabled in the `dev` profile; off elsewhere unless `SWAGGER_UI_ENABLED=true` |
| OpenAPI document | http://localhost:8080/v3/api-docs | What `npm run api:types` reads |
| Health | http://localhost:8080/actuator/health | Liveness and readiness groups |
| Postgres | `jdbc:postgresql://localhost:5432/mizan` | user `mizan`, password `mizan` (dev only) |
| MinIO S3 endpoint | http://localhost:9000 | Bucket `mizan-attachments`, created by the API on startup |
| MinIO console | http://localhost:9001 | user `mizan`, password `mizan-dev-secret` (dev only) |

Every credential above is a dev default from `.env.example` and `application.yml`. Deployed
environments override them with `MIZAN_*`, `DB_*` and `MINIO_*` environment variables.

### API endpoints (v1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | none | Create an account with an email or phone; returns tokens and creates the household |
| POST | `/api/v1/auth/login` | none | Sign in; five wrong passwords lock the account for 15 minutes |
| POST | `/api/v1/auth/refresh` | cookie or body | Rotate the refresh token; replaying an old one revokes every session |
| POST | `/api/v1/auth/logout` | cookie or body | Revoke the refresh token |
| POST | `/api/v1/auth/verify` | bearer | Confirm the email or phone with the delivered code |
| POST | `/api/v1/auth/verify/resend` | bearer | Send a fresh code (60 s cooldown) |
| POST | `/api/v1/auth/password-reset/request` | none | Send a reset code; always 204 |
| POST | `/api/v1/auth/password-reset/confirm` | none | Set a new password; ends all sessions |
| GET | `/api/v1/me` | bearer | The signed-in account and its household |
| GET | `/api/v1/households/current` | bearer | The caller's household |
| PATCH | `/api/v1/households/current` | bearer, `PLAN_EDIT` | Rename or change the month start day |
| POST | `/api/v1/sync/devices` | bearer | Register the calling device |
| POST | `/api/v1/sync/push` | bearer, `RECORD_WRITE` | Push local changes; per-field last-write-wins |
| GET | `/api/v1/sync/pull?deviceId&since&limit` | bearer | Pull the change feed after a sequence number |
| GET | `/api/v1/sync/conflicts?deviceId` | bearer | Edits from this device that lost to newer values |

The access token goes in `Authorization: Bearer <token>` and lives 15 minutes. The refresh token
is a 30-day httpOnly cookie scoped to `/api/v1/auth`, and is also returned in the body for
clients without a cookie jar. Errors are RFC 7807 `application/problem+json`; branch on `code`.

### Web app routes

| Route | Screen |
|---|---|
| `/login`, `/register`, `/reset` | Sign in, create account, password reset |
| `/` | Home: greeting, cash on hand, verification prompt |
| `/verify` | Enter the 6-digit contact verification code |
| `/accounts` | Cash accounts: add, edit, soft-delete with undo |
| `/sync` | Sync status, sync now, overridden edits (conflict log) |
| `/settings` | Language, theme, lock timeout, PIN, household settings, install, sign out |

A PIN screen appears after first sign-in and whenever the app has been in the background longer
than the configured timeout (30 seconds by default).

## Tests

```bash
cd api && ./mvnw test
```

Integration tests run against a real Postgres in Testcontainers, so Docker must be running.

```bash
cd web && npm test && npm run typecheck
```

## Layout

- `api/src/main/java/iq/mizan/<module>` — one package per module (`auth`, `household`, `sync`, `asset`, …) with
  `controller`, `dto`, `entity`, `mapper`, `repository`, `service`. `common` holds security, tenancy,
  audit, errors and storage. `domain` is pure money math with no Spring.
- `api/src/main/resources/db/migration` — Flyway migrations. Every syncable table is created with
  `call make_syncable('<table>')`, which adds the change-feed trigger and the row-level security policy.
- `web/src/features/<feature>` — screens. `db/` is the local store and the only place that writes to
  syncable tables. `sync/` is the change-log engine. `lock/` is the PIN and biometric lock.
- `shared/test-vectors/` — one JSON file per business rule, loaded by both test suites.

See [CLAUDE.md](CLAUDE.md) for the coding conventions.
