# Mizan runbook

Operations for a hosted Mizan. Local development is in the README.

## Deploy

1. Copy `.env.example` to `.env` on the host. Set `SPRING_PROFILES_ACTIVE=prod`, real values for
   `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`, `MIZAN_JWT_SECRET` (32+ bytes), `MIZAN_HASH_PEPPER`,
   `SITE_ADDRESS` (the public host; Caddy obtains the certificate) and `MIZAN_ALLOWED_ORIGINS`
   (`https://<host>`). Add provider keys under `MIZAN_*_API_KEY` and the parallel rate.
2. `docker compose --profile prod up -d --build`. Flyway migrates on API start; the API creates
   the attachments bucket; Caddy serves the PWA on 80/443 and proxies `/api`.
3. Check `https://<host>/actuator/health` and that `GET /api/v1/market/quotes` (with a token)
   shows real sources rather than `stub`.

Upgrades are the same command. Migrations are forward-only; never edit an applied one.

## Backups and restore

The `backup` service runs `pg_dump` nightly into the `mizan-backups` bucket in MinIO and prunes
files older than `BACKUP_RETENTION_DAYS`. Attachments live in the `mizan-attachments` bucket;
back that bucket up with `mc mirror` to another store, since a database dump does not include it.

Restore into an empty database:

```bash
docker compose --profile prod stop api
docker compose exec minio mc alias set local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
docker compose exec minio mc cat local/mizan-backups/<file>.sql.gz | gunzip | docker compose exec -T postgres psql -U mizan mizan
docker compose --profile prod start api
```

Devices keep their own copy of the household, so after a restore they push anything the dump
missed on the next sync; the per-field clocks decide, not the restore time.

## Secrets

- **JWT secret**: rotating it invalidates every access token (15 min lifetime) but not refresh
  tokens, which are stored by hash. Users refresh silently.
- **Hash pepper**: rotating it invalidates every refresh token and pending verification code.
  Everyone signs in again. Rotate only with that expectation.
- **Postgres and MinIO passwords**: change in `.env`, then `docker compose down` and `up`. The
  Postgres volume keeps the old password until you `ALTER ROLE` inside the container.
- **Settings key** (`MIZAN_SETTINGS_KEY`, 32 random bytes, base64: `openssl rand -base64 32`):
  encrypts the secrets operators save in the dashboard. Keep it out of the database and its
  backups. Losing or changing it makes saved secrets unreadable: re-enter them in the dashboard.

## Price feeds

Providers are tried in order: IQWealth for both dollar rates (from its last scheduled poll),
Baghdad Bullion House for local gold and silver, metals.dev and goldapi.io when keyed,
gold-api.com, open.er-api.com and openexchangerates.org, then the manual market dollar, then the
stub (dev only). A provider without a key is skipped. Keys, on/off switches, the manual dollar,
staleness and the Refresh cooldown can be changed in the admin dashboard without a restart (ADR
admin-1); the environment values are the defaults. If every provider fails, the last quote
stays and turns `stale` after 24 hours; the app labels it and users can enter today's price
themselves, which wins over the feed. Every change of a price is kept (ADR prices-2).

## Local dealer price feed

Any JSON endpoint can supply the local market's price. Set `MIZAN_LOCAL_DEALER_URL` and the
dot-separated paths and units, for example a file you maintain at
`https://example.iq/prices.json` containing `{"gold21": 547000, "silver": 1480}`:

```bash
MIZAN_LOCAL_DEALER_URL=https://example.iq/prices.json
MIZAN_LOCAL_DEALER_GOLD_PATH=gold21
MIZAN_LOCAL_DEALER_GOLD_UNIT=MITHQAL_21K
MIZAN_LOCAL_DEALER_SILVER_PATH=silver
MIZAN_LOCAL_DEALER_SILVER_UNIT=GRAM_999
```

Units: `GRAM_24K`, `GRAM_22K`, `GRAM_21K`, `GRAM_18K`, `MITHQAL_*` of the same purities,
`GRAM_999`, `GRAM_925`. The quote is stored as `XAU_LOCAL`/`XAG_LOCAL` in IQD per gram of pure
metal and refreshed with the other feeds. Households opt in under valuation settings.

## Receipt OCR assets

The web build fetches Tesseract models from GitHub; set `MIZAN_TESSDATA_URL` to a mirror when
the build host cannot reach it. The files live in `web/public/ocr/` and are served as static
assets; nothing about a receipt reaches the server. All three Tesseract cores are copied
(plain, SIMD, relaxed SIMD); the browser picks one, and a missing one breaks reading only on
browsers that pick it. The service worker never caches an HTML answer under `/ocr/`.

## Push notifications

Web Push needs a VAPID key pair that identifies this server to Apple, Google and Mozilla. Without
one the API generates a pair at startup and logs the public key; subscriptions then break at the
next restart, so a real deployment sets both:

```bash
# Prints a fresh pair; put them in .env as MIZAN_VAPID_PUBLIC_KEY and MIZAN_VAPID_PRIVATE_KEY.
cd api && ./mvnw -q compile && java -cp target/classes iq.mizan.notification.push.VapidKeyTool
```

`MIZAN_VAPID_SUBJECT` is a `mailto:` address the push services can contact. The daily job runs
at `mizan.notifications.daily-cron` in `mizan.notifications.time-zone` (Asia/Baghdad). Set
`MIZAN_NOTIFICATIONS_TRANSPORT=log` to print instead of sending.

## Logs and performance in Kibana

The `observability` profile runs a single-node Elasticsearch with security off, Kibana bound to
localhost and Logstash; `--profile containers` adds Metricbeat for per-container resource use. Reach Kibana over an SSH tunnel:

```bash
ssh -L 5601:localhost:5601 user@host
```

`kibana-setup` recreates the data views and the "Mizan overview" dashboard on every start
(`infra/observability/kibana`). Log lines are JSON as the API writes them (see README); search
by `correlationId` to follow one request through every line, or by `traceId` when several
services are involved. Retention is manual: delete old indices with

```bash
curl -X DELETE 'http://localhost:9200/mizan-logs-2026.08.*'
```

or set an ILM policy on `mizan-logs-*`, `containers-logs-*` and `metricbeat-*` once volumes matter.
Index templates live in `infra/observability/*-template.json`; Logstash installs them on start,
so delete an index created before a template change for the new mapping to apply. Give Elasticsearch
more heap with `ES_JAVA_OPTS` in `.env` when the host has room; 512 MB is the floor.

## Scheduled jobs

| Job | Default | What it does |
|---|---|---|
| Reminder dispatch | every 5 minutes | Sends the reminders devices scheduled (kinds and times only), once each |
| Metal price alerts | 09:00 Asia/Baghdad | Public gold and silver moves beyond each member's threshold |
| Market poll | every 30 min, 07:00–22:00 Asia/Baghdad | Refreshes every feed; records a price change only when it moves |
| IQWealth dollar | every 20 min 08:00–22:00, and 23:00, 02:00, 05:00 | One request each, never past the reserve |
| Attachment object purge | 03:15 | Removes storage objects (receipts, backups) whose rows left the recycle bin |
| Household deletion purge | 04:15 | Deletes households seven days after the owner confirmed |

| Job | When (server time) | What it does |
|---|---|---|
| Market refresh | off unless `MIZAN_MARKET_AUTO_REFRESH` | Timed refresh of every feed |
| Recycle-bin purge | 03:30 daily | Hard-deletes rows soft-deleted more than 30 days ago |
| Account purge | 03:45 daily | Deletes accounts and households whose deletion request is older than 30 days |

Every instance schedules every job, and the `scheduled_job_lock` table lets one of them run it
(ADR csp-and-job-lock-1). A row names the job, the instance that took it (`pid@host/uuid`) and
`locked_until`. A crashed run frees itself when `locked_until` passes (one hour for the purges,
minutes for the polls). To free a job by hand:

```sql
update scheduled_job_lock set locked_until = now() where name = 'iqwealth-poll';
```

The IQWealth rates live in the polling instance's memory; the others answer from the stored
quote it wrote, so every instance shows the same dollar. The operator's "Fetch now" polls from
whichever instance served it, outside the lock.

## Row-level security

Every household table is `FORCE ROW LEVEL SECURITY`. Requests run as the `mizan_app` role with
`app.user_id` and `app.household_id` bound per transaction. Server jobs run as the connection
user with `app.system = on`, which the policies accept. Only application code issues SQL, so the
bypass is not reachable from the API surface, but any new code that opens a raw connection must
bind a household or the system flag or it will see an empty database.

## Security notes from the v1 review

- Passwords: Argon2id. Refresh tokens: 30-day httpOnly `SameSite=Strict` cookie scoped to
  `/api/v1/auth`, rotated on use; a replayed token revokes every session and is audited.
- Unauthenticated auth endpoints are rate-limited per client address, in process.
- Presigned attachment URLs are issued only for rows the caller can see and expire in 15 minutes;
  the bytes are encrypted on the device before upload, the API never holds a key.
- Caddy sends a Content-Security-Policy (ADR csp-and-job-lock-2) with `X-Content-Type-Options`,
  `X-Frame-Options: DENY`, `Referrer-Policy` and a `Permissions-Policy`, all from
  `infra/caddy/security-headers.caddy`. Scripts come only from the site itself (plus
  `'wasm-unsafe-eval'` for OCR); `MIZAN_STORAGE_ORIGIN` is the one other origin, for receipts.
  Set it to the origin browsers use for object storage, or uploads are blocked. `vite preview`
  serves the same file, so `npm run build && npx vite preview` shows a violation before
  production does. Never add an inline `<script>` to `index.html`; boot code goes in `public/`.
- `npm audit --audit-level=high` runs in CI. Maven dependencies are pinned through the Boot BOM;
  review them on each Boot upgrade.
- Known gap: the parallel dollar rate and stub prices are configuration, not signed data. Anyone
  with `.env` access can move every valuation.

## Support without impersonation

Support never signs in as a user, and since ADR e2e-1 nobody at the service can read a household's
records. Ask the person to export their data from Settings and share the file; import it into a
scratch household to reproduce. Audit entries (`audit_entry`) record every sign-in, sync write
(field names only), deletion request and operator action with actor and time.

## Operators and the admin dashboard

- **First operator**: register the account in the app, set `MIZAN_BOOTSTRAP_OPERATOR` to its
  email or phone and restart. Later operators are granted on the dashboard's Operators tab; the
  last operator cannot be removed.
- **Using it**: operators see **Admin** in the menu (`/admin`). Reading needs only the role. Every
  change asks for the password again and then allows changes for 10 minutes
  (`mizan.admin.elevation`).
- **What it can do**: feed health and today's IQWealth quota, "fetch now"; the settings listed in
  `SettingKey`; correct or remove a price; find an account, suspend or restore it, sign it out
  everywhere, cancel its deletion request; announcements in English and Arabic; the audit log;
  operators.
- **What it cannot do**: see balances, transactions, names or notes; recover someone's recovery
  code. A suspended person keeps their encrypted data and gets it back when restored.
- **Settings** reach every instance within 15 seconds. Removing an operator's value returns the
  environment default. Without `MIZAN_SETTINGS_KEY`, secrets can only come from the environment.
