# ADR storage-1 — SeaweedFS replaces MinIO for object storage

- Date: 2026-11-18
- Status: accepted (supersedes the MinIO choice in ADR foundation-1)
- Task: T-seaweedfs

## Context
During 2025 MinIO stripped its community console, stopped publishing community images and
binaries, and put the community edition in maintenance mode, so `minio/minio:latest` would get
no further fixes. The owner asked for SeaweedFS. Alternatives considered: Garage (small, AGPL),
RustFS (Apache 2.0, young), Ceph (far too heavy for one host). The API speaks plain S3 (put,
get, delete, list, presigned URLs, path-style), so the store is swappable.

## Decision
- One `chrislusf/seaweedfs` container, pinned (4.51), runs `weed server -s3` with the master,
  volume server, filer and S3 gateway in one process on port 8333 and data in `seaweeddata`.
- Its S3 identity is written from `MIZAN_STORAGE_ACCESS_KEY` / `MIZAN_STORAGE_SECRET_KEY` at
  start; the old `MINIO_ROOT_*` names are read as a fallback so existing `.env` files work.
- Volumes are 1 GB and their count follows free disk (`-volume.max=0`); vacuum runs at 10%
  garbage, so deleted receipts give their space back sooner.
- The backup image uses `rclone` (provider SeaweedFS) instead of MinIO's `mc`.

## Consequences
- No MinIO component remains; the store has an active Apache 2.0 project behind it.
- There is no admin console like MinIO's; `weed shell` and `rclone` are the tools.
- Keys containing `"` or `\` would break the generated identity file; generate plain ones.
- Moving existing data is a one-time `rclone copy` per bucket (RUNBOOK).
