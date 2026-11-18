# ADR storage-3 — Database dumps: custom format, seven daily and four weekly

- Date: 2026-11-18
- Status: accepted
- Task: T-backup-retention

## Context
The nightly `pg_dump` was plain SQL through gzip, and every dump of the last 30 days was kept
(30 files). The owner asked for 7 dailies and 4 weeklies in Postgres's compressed dump format.
Measured on the dev database: plain SQL + gzip 34.9 KB; custom format with its own compression
(gzip or zstd) 222 KB, because the custom format stores its table of contents (the schema:
row-level security policies, triggers, functions) uncompressed and that is most of a Mizan
dump; custom format uncompressed and then zstd -19 37.9 KB; plain SQL + zstd -19 31.0 KB.

## Decision
- Dumps use the custom format (`pg_restore`: table-by-table restore, `--list`) with its own
  compression off, piped through `zstd -19`: `mizan-<UTC stamp>.dump.zst`.
- After each run the script keeps the newest `BACKUP_KEEP_DAILY` (7) files and, older than
  those, the newest file of each of `BACKUP_KEEP_WEEKLY` (4) ISO weeks; it deletes the rest.
  Only files named like its dumps are touched; old `.sql.gz` dumps are pruned by the same rule.
- Pruning is by count, not age, so a stopped backup never deletes the last good dumps.

## Consequences
- 11 files instead of 30, each about the size of the old gzip dump, covering about five weeks.
- Restoring needs `zstd -d` before `pg_restore` (both are in the backup image).
- Plain SQL with zstd would be about 20% smaller still; the custom format was kept for its
  restore options, as the owner asked.
