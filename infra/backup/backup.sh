#!/bin/sh
# Nightly logical backup of Postgres into object storage, keeping the last N days.
# Restore: rclone cat store:mizan-backups/<file> | gunzip | psql "$DATABASE_URL"
set -eu
export RCLONE_CONFIG_STORE_TYPE=s3
export RCLONE_CONFIG_STORE_PROVIDER=SeaweedFS
export RCLONE_CONFIG_STORE_ENDPOINT="$STORAGE_ENDPOINT"
export RCLONE_CONFIG_STORE_ACCESS_KEY_ID="$STORAGE_ACCESS_KEY"
export RCLONE_CONFIG_STORE_SECRET_ACCESS_KEY="$STORAGE_SECRET_KEY"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="mizan-${STAMP}.sql.gz"
rclone mkdir "store:${BACKUP_BUCKET}"
pg_dump "$DATABASE_URL" | gzip | rclone rcat "store:${BACKUP_BUCKET}/${FILE}"
rclone delete --min-age "${BACKUP_RETENTION_DAYS}d" "store:${BACKUP_BUCKET}" || true
echo "backup ${FILE} written"
