#!/bin/sh
# Nightly backup of Postgres into object storage in pg_dump's custom format, restorable table by
# table, compressed whole with zstd (ADR storage-3). Keeps the newest BACKUP_KEEP_DAILY dumps and,
# before those, the newest dump of each of BACKUP_KEEP_WEEKLY ISO weeks.
# Restore: rclone cat store:mizan-backups/<file>.dump.zst | zstd -d | pg_restore --no-owner -d "$DATABASE_URL"
set -eu
export RCLONE_CONFIG_STORE_TYPE=s3
export RCLONE_CONFIG_STORE_PROVIDER=SeaweedFS
export RCLONE_CONFIG_STORE_ENDPOINT="$STORAGE_ENDPOINT"
export RCLONE_CONFIG_STORE_ACCESS_KEY_ID="$STORAGE_ACCESS_KEY"
export RCLONE_CONFIG_STORE_SECRET_ACCESS_KEY="$STORAGE_SECRET_KEY"
KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"
STORE="store:${BACKUP_BUCKET}"

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="mizan-${STAMP}.dump.zst"
rclone mkdir "$STORE"
# The custom format leaves its schema description uncompressed, and Mizan's policies and
# triggers make that most of a dump, so compressing the whole file is about 6x smaller.
pg_dump --format=custom --compress=0 "$DATABASE_URL" | zstd -19 -q -c | rclone rcat "$STORE/$FILE"
echo "backup ${FILE} written"

# Names sort by time; older .sql.gz dumps from before the custom format are pruned the same way.
week_of() {
  day=$(echo "$1" | sed -E 's/^mizan-([0-9]{4})([0-9]{2})([0-9]{2})T.*/\1-\2-\3/')
  date -u -d "$day" +%G-%V
}
kept=0
weeks=""
rclone lsf --files-only "$STORE" | grep -E '^mizan-[0-9]{8}T[0-9]{6}Z\.(dump\.zst|sql\.gz)$' | sort -r | while read -r name; do
  kept=$((kept + 1))
  if [ "$kept" -le "$KEEP_DAILY" ]; then
    continue
  fi
  week=$(week_of "$name")
  case " $weeks " in
    *" $week "*) ;;
    *)
      if [ "$(echo "$weeks" | wc -w)" -lt "$KEEP_WEEKLY" ]; then
        weeks="$weeks $week"
        continue
      fi
      ;;
  esac
  rclone deletefile "$STORE/$name"
  echo "pruned ${name}"
done
