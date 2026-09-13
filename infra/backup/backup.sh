#!/bin/sh
# Nightly logical backup of Postgres into the MinIO bucket, keeping the last N days.
# Restore: mc cp local/mizan-backups/<file> - | gunzip | psql "$DATABASE_URL"
set -eu
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="mizan-${STAMP}.sql.gz"
mc alias set local "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing "local/${BACKUP_BUCKET}" >/dev/null
pg_dump "$DATABASE_URL" | gzip | mc pipe "local/${BACKUP_BUCKET}/${FILE}"
mc rm --recursive --force --older-than "${BACKUP_RETENTION_DAYS}d" "local/${BACKUP_BUCKET}" >/dev/null || true
echo "backup ${FILE} written"
