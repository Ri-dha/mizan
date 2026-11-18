# T-backup-retention — Keep seven daily and four weekly database dumps
- Status: done (2026-11-18), d8a987e on develop
- Context: ADR storage-3; owner's request after the storage review. Builds on T-seaweedfs
  (rclone in the backup image).
- Notes: `infra/backup/backup.sh` and `Dockerfile` (zstd), `BACKUP_KEEP_DAILY` /
  `BACKUP_KEEP_WEEKLY` replace `BACKUP_RETENTION_DAYS` in compose and `.env.example`. Tested
  against SeaweedFS with 59 seeded dumps: 11 kept (7 newest, then 11 Nov, 8 Nov, 1 Nov, 25 Oct),
  49 pruned, an unrelated file left; the newest restored into a scratch database matched the
  live one (schema 024, users, 32 policies).
