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

## Price feeds

Providers are tried in order: metals.dev, goldapi.io, open.er-api.com, openexchangerates.org,
then the configured parallel rate, then the stub (dev only). Each real provider is registered
only when its key is set, so swapping providers is a `.env` change and a restart. If every
provider fails, the last quote stays and turns `stale` after 24 hours; the app labels it and
users can enter today's price themselves, which wins over the feed.

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

## Scheduled jobs

| Job | Default | What it does |
|---|---|---|
| Notifications | 08:00 Asia/Baghdad | Evaluates reminders per subscribed member and sends them once |
| Attachment object purge | 03:15 | Removes storage objects (receipts, backups) whose rows left the recycle bin |
| Household deletion purge | 04:15 | Deletes households seven days after the owner confirmed |

| Job | When (server time) | What it does |
|---|---|---|
| Market refresh | on start, then hourly | Fetches XAU, XAG and dollar rates; records one history point per day |
| Month close | 00:15 daily | Snapshots the household month that ended, unless already closed by hand |
| Recycle-bin purge | 03:30 daily | Hard-deletes rows soft-deleted more than 30 days ago |
| Account purge | 03:45 daily | Deletes accounts and households whose deletion request is older than 30 days |

All run in the single API instance. If you scale to more than one instance, guard them with a
lock (ShedLock or a Postgres advisory lock) before doing so.

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
- Caddy adds `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` and a
  `Permissions-Policy`. There is no CSP yet; adding one means hashing the inline theme script.
- `npm audit --audit-level=high` runs in CI. Maven dependencies are pinned through the Boot BOM;
  review them on each Boot upgrade.
- Known gap: the parallel dollar rate and stub prices are configuration, not signed data. Anyone
  with `.env` access can move every valuation.

## Support without impersonation

Support never signs in as a user. Ask the person to export their data from Settings and share the
file; import it into a scratch household to reproduce. Audit entries (`audit_entry`) record every
sign-in, sync write, month close and deletion request with actor and time.
