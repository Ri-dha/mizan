# Mizan runbook

Operations for a hosted Mizan. Local development is in the README.

## Deploy

1. Copy `.env.example` to `.env` on the host. Set `SPRING_PROFILES_ACTIVE=prod`, real values for
   `POSTGRES_PASSWORD`, `MIZAN_STORAGE_SECRET_KEY`, `MIZAN_JWT_SECRET` (32+ bytes), `MIZAN_HASH_PEPPER`,
   `SITE_ADDRESS` (the public host; Caddy obtains the certificate), `MIZAN_ALLOWED_ORIGINS`
   (`https://<host>`) and `MIZAN_STORAGE_PUBLIC_ENDPOINT` (`https://<host>`, for receipt links). Add provider keys under `MIZAN_*_API_KEY` and the parallel rate.
2. `docker compose --profile prod up -d --build`. Flyway migrates on API start; the API creates
   the attachments bucket; Caddy serves the PWA on 80/443 and proxies `/api`.
3. Check `https://<host>/actuator/health` and that `GET /api/v1/market/quotes` (with a token)
   shows real sources rather than `stub`.

Upgrades are the same command. Migrations are forward-only; never edit an applied one.

## Backups and restore

The `backup` service runs `pg_dump` nightly into the `mizan-backups` bucket in SeaweedFS with
`rclone` (ADR storage-3). Dumps are pg_dump's custom format, compressed whole with zstd
(`mizan-<UTC stamp>.dump.zst`, about 40 KB for a near-empty database). After each run it keeps
the newest `BACKUP_KEEP_DAILY` (7) dumps and, before those, the newest dump of each of
`BACKUP_KEEP_WEEKLY` (4) ISO weeks, so about five weeks are covered by eleven files. Attachments live in the
`mizan-attachments` bucket; copy it to another store with `rclone sync`, since a database dump
does not include it. Receipt photos there last 30 days anyway (ADR storage-2).

Restore into an empty database:

```bash
docker compose --profile prod stop api
docker compose --profile prod run --rm backup sh -c 'export RCLONE_CONFIG_STORE_TYPE=s3 RCLONE_CONFIG_STORE_PROVIDER=SeaweedFS RCLONE_CONFIG_STORE_ENDPOINT="$STORAGE_ENDPOINT" RCLONE_CONFIG_STORE_ACCESS_KEY_ID="$STORAGE_ACCESS_KEY" RCLONE_CONFIG_STORE_SECRET_ACCESS_KEY="$STORAGE_SECRET_KEY"; rclone cat store:mizan-backups/<file>.dump.zst | zstd -d | pg_restore --no-owner -d "$DATABASE_URL"'
docker compose --profile prod start api
```

List the dumps with `rclone lsf store:mizan-backups` inside that same `run`. One table can be
restored alone with `pg_restore -t <table>`. Dumps from before ADR storage-3 are `.sql.gz`:
`rclone cat … | gunzip | psql`.

Devices keep their own copy of the household, so after a restore they push anything the dump
missed on the next sync; the per-field clocks decide, not the restore time.

## Secrets

- **JWT secret**: rotating it invalidates every access token (15 min lifetime) but not refresh
  tokens, which are stored by hash. Users refresh silently.
- **Hash pepper**: rotating it invalidates every refresh token and pending verification code.
  Everyone signs in again. Rotate only with that expectation.
- **Postgres and storage passwords**: change in `.env`, then `docker compose down` and `up`. SeaweedFS
  reads its key from the env on every start, so the storage key changes with no other step. The
  Postgres volume keeps the old password until you `ALTER ROLE` inside the container.
- **Settings key** (`MIZAN_SETTINGS_KEY`, 32 random bytes, base64: `openssl rand -base64 32`):
  encrypts the secrets operators save in the dashboard. Keep it out of the database and its
  backups. Losing or changing it makes saved secrets unreadable: re-enter them in the dashboard.

## Price feeds

Providers are tried in order: IQWealth for both dollar rates (from its last scheduled poll);
for gold, IQWealth first by default (the 24-karat gram and the world ounce, polled four times a
day) and Baghdad Bullion House after it, swapped with the dashboard setting "Main gold price
source" (ADR prices-3), with the dealer's buying price derived below IQWealth's gram by the
dashboard's buying margin (default 1%, ADR prices-4); Baghdad Bullion House for silver; metals.dev and goldapi.io when keyed,
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

Phones are reached through Expo's push service (ADR push-1), which relays to APNs and FCM with
the credentials held in the app's EAS project, so the server needs no Apple or Google key. If
the EAS project enforces push security, put an access token in `.env` as
`MIZAN_EXPO_ACCESS_TOKEN`; `MIZAN_EXPO_PUSH_URL` overrides the endpoint (tests point it at a stub).

## API memory and connections

The API image runs with `JAVA_OPTS` set for one small instance: serial collector, 192 MB heap,
160 MB metaspace, 64 MB code cache, and it exits on running out of memory so Docker restarts it
(measured about 480 MB in use). Compose caps the container at `API_MEM_LIMIT` (640m). Raise
`-Xmx` through `API_JAVA_OPTS` and the limit together if the heap fills (`jvm.memory.used` in
the metrics). Production keeps 2 to 6 database connections (`DB_POOL_MIN_IDLE`, `DB_POOL_MAX`).
Every transaction binds the tenancy settings in one statement (`TenantSession.bind`).

Access lines are INFO only for errors, client errors and requests over
`MIZAN_SLOW_REQUEST` (1s); fast successful ones are DEBUG (ADR obs-3). For a full trace of
requests set `LOGGING_LEVEL_IQ_MIZAN_HTTP=DEBUG`.

## Logs and performance in Kibana

The `observability` profile (and `prod`) runs a single-node Elasticsearch with security off and
Filebeat (ADR obs-4). Metrics are not here any more: see "Metrics, alerts and uptime" (ADR obs-5).
Kibana (about 1.2 GB of memory) runs only with its own profile, so start it when you need it and
stop it after:

```bash
docker compose --profile prod --profile kibana up -d kibana kibana-setup
docker compose --profile prod --profile kibana stop kibana
```

Reach Kibana over an SSH tunnel:

```bash
ssh -L 5601:localhost:5601 user@host
```

`kibana-setup` recreates the data views and the "Mizan overview" dashboard on every start
(`infra/observability/kibana`). Log lines are JSON as the API writes them (see README); search
by `correlationId` to follow one request through every line, or by `traceId` when several
services are involved.

Retention (ADR obs-2): `elasticsearch-setup` runs before Filebeat and writes three lifecycle
policies that delete daily indexes once they reach their age. Days are set in `.env`:

| Indexes | Policy | Default | Variable |
|---|---|---|---|
| `mizan-logs-*` (the API) | `mizan-logs-retention` | 14 days | `MIZAN_LOG_RETENTION_DAYS` |
| `containers-logs-*` | `containers-logs-retention` | 7 days | `MIZAN_CONTAINER_LOG_RETENTION_DAYS` |
| `mizan-metrics-*`, `metricbeat-*` (from before ADR obs-5, ageing out) | `metrics-retention` | 7 days | `MIZAN_METRICS_RETENTION_DAYS` |

Changing a value takes effect on the next `docker compose --profile prod up`; existing indexes
follow the new age. Indexes use `best_compression` and no replicas. Check what is held with

```bash
curl -s 'localhost:9200/_cat/indices?v&s=index&h=index,docs.count,store.size'
curl -s 'localhost:9200/mizan-logs-*/_ilm/explain?filter_path=**.phase,**.age'
```

Every container's own output file is capped by Docker at three files of 10 MB
(`x-logging` in `docker-compose.yml`), so the host disk has a ceiling even with the profile off.
Index templates live in `infra/observability/elasticsearch/*-template.json`; `elasticsearch-setup`
installs them on every start, so delete an index created before a template change for the new
mapping to apply. Filebeat reads only `*-json.log` (the json-file driver's files, not Docker's
binary `container-cached.log`) and keeps its read position in the `filebeatdata` volume. Give Elasticsearch
more heap with `ES_JAVA_OPTS` in `.env` when the host has room; 512 MB is the floor.

## Metrics, alerts and uptime (ADR obs-5)

Part of `observability` and `prod`; every web UI listens on 127.0.0.1, so tunnel in:

```bash
ssh -L 3000:localhost:3000 -L 3001:localhost:3001 -L 9093:localhost:9093 user@host
```

- **Grafana** (`:3000`, user `admin`, password `MIZAN_GRAFANA_ADMIN_PASSWORD`; change it in `.env`
  before the first start, as Grafana keeps the first one). The "Mizan metrics" dashboard, the
  `Mizan logs` Elasticsearch source for searching logs, and the firing alerts.
- **Alerts**: rules in `infra/monitoring/vmalert/rules.yml`. To receive them on Telegram, create a
  bot with @BotFather, send it a message, read the chat id from
  `https://api.telegram.org/bot<token>/getUpdates`, set `MIZAN_ALERT_TELEGRAM_BOT_TOKEN` and
  `MIZAN_ALERT_TELEGRAM_CHAT_ID`, and recreate `alertmanager`. Without them alerts only show in
  Grafana and Alertmanager (`:9093`).
- **Uptime Kuma** (`:3001`): create the account on the first visit, then add monitors:
  HTTP `http://api:8080/actuator/health` (keyword `UP`), HTTP `https://<your host>` (with
  certificate expiry), and a **Push** monitor with a 26-hour interval whose URL goes in
  `BACKUP_PUSH_URL` (use `http://uptime-kuma:3001/api/push/<token>` so the backup reaches it
  inside Docker). Add a notification (Telegram, email) under Settings. Kuma runs on the same host,
  so it cannot report the whole server being down; add a free external check on the public address
  for that.
- **VictoriaMetrics** (`:8428/vmui`) keeps `MIZAN_METRICS_RETENTION` (30 days) and holds its
  caches to `MIZAN_VICTORIAMETRICS_MEMORY` (96 MB).
- cAdvisor cannot see individual containers under Docker Desktop on a Mac; on a Linux host it can.

Measured in use (2026-11-21): Grafana 226 MB, VictoriaMetrics 141 MB, Uptime Kuma 112 MB,
Alertmanager 41 MB, vmalert 27 MB, cAdvisor 25 MB, node-exporter and postgres-exporter 10 MB each.

## Object storage (SeaweedFS)

One `seaweedfs` container (ADR storage-1) runs the master, volume server, filer and S3 gateway
on port 8333, storing everything in the `seaweeddata` volume. Its S3 identity is written from
`MIZAN_STORAGE_ACCESS_KEY` and `MIZAN_STORAGE_SECRET_KEY` at start (the old `MINIO_ROOT_*` names
are still read). Space from deleted files is reclaimed by vacuum once a volume is 10% garbage.

Browsers and phones never use the internal address (ADR storage-4). Set
`MIZAN_STORAGE_PUBLIC_ENDPOINT` to the site, e.g. `https://mizan.example.iq`: the API signs upload
and download links for it, and Caddy forwards `/mizan-attachments/*` to `seaweedfs:8333` with the
`Host` header unchanged, which keeps the signature valid. SeaweedFS also gets the address as
`S3_EXTERNAL_URL`, so it still verifies if a proxy in front rewrites `Host`. Port 8333 is bound to
127.0.0.1; with the variable blank (development) links point at `http://localhost:8333`. A receipt
that fails to upload with 403 `SignatureDoesNotMatch` means the link's host and the host storage
received differ.
Check what is held:

```bash
docker compose exec seaweedfs sh -c 'echo "volume.list" | weed shell -master=localhost:9333' | head
```

Receipt photos (`receipts/` prefix) are deleted 30 days after upload by the receipt expiry job
(`MIZAN_RECEIPT_RETENTION`, default `30d`). Change it only together with
`RECEIPT_RETENTION_DAYS` in `web/src/attachments/store.ts` and the manual, since the app tells
people the number.

Moving from MinIO: start `seaweedfs`, then copy the buckets once with
`rclone copy minio:mizan-attachments store:mizan-attachments` (and `mizan-backups`). Receipts
uploaded before this change sit outside `receipts/`, so they never expire; there were none in
any deployment when the switch was made.

## Scheduled jobs

| Job | Default | What it does |
|---|---|---|
| Reminder dispatch | every 5 minutes | Sends the reminders devices scheduled (kinds and times only), once each |
| Metal price alerts | 09:00 Asia/Baghdad | Public gold and silver moves beyond each member's threshold |
| Market poll | every 30 min, 07:00–22:00 Asia/Baghdad | Refreshes every feed; records a price change only when it moves |
| IQWealth dollar | every 15 min 08:00–21:45, and 23:00, 02:00, 05:00 (59 a day) | One request each, never past the reserve |
| Receipt photo expiry | 03:00 | Deletes receipt photos 30 days after upload (ADR storage-2) |
| Attachment object purge | 03:15 | Removes storage objects (receipts, backups) whose rows left the recycle bin |
| Household deletion purge | 04:15 | Deletes households seven days after the owner confirmed |

| Job | When (server time) | What it does |
|---|---|---|
| Market refresh | off unless `MIZAN_MARKET_AUTO_REFRESH` | Timed refresh of every feed |
| Recycle-bin purge | 03:30 daily | Hard-deletes rows soft-deleted more than 30 days ago |
| Sync housekeeping | 03:40 daily | Keeps the newest `sync_log` entry per record; drops push receipts after `receipt-retention` (30 days) |
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
