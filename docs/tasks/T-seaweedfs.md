# T-seaweedfs — Replace MinIO with SeaweedFS
- Status: in-review (2026-11-18), branch feat/log-retention (worktree ../.wt-log-retention), uncommitted
- Context: ADR storage-1. Owner's request after MinIO's community edition went into maintenance.
- Notes: compose `seaweedfs` service (4.51), API and backup pointed at `seaweedfs:8333`, backup
  image on `rclone`, default ports moved from 9000 to 8333 (application.yml, CSP origin, vite
  preview, `.env.example`), docs. Public endpoint still open: T-storage-public-endpoint.
