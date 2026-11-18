# Handover — 2026-11-18 — Use the least storage: logs, receipts, SeaweedFS

## Completed (on develop)
- T-log-retention (ADR obs-2): see its handover.
- T-receipt-retention (ADR storage-2): grey 1200 px photos, `kind` on attachment URLs,
  `receipts/` prefix, `ReceiptPhotoExpiry`, device cleanup, notices in en and ar, manual.
- T-seaweedfs (ADR storage-1): compose service, rclone backups, ports 8333, docs.
- T-backup-retention (ADR storage-3): custom-format dumps compressed whole with zstd, 7 daily
  + 4 weekly kept.
- Fix: `AttachmentObjectPurge` now runs in a transaction (learning 2026-11-18-scheduled-self-call).

## In flight
- Nothing. Committed as 561acf2, 6b0c2d4, d8a987e and fde03d9, merged into develop and pushed.

## Next steps
- T-storage-public-endpoint, now that the store is settled.
- Remaining storage idea not chosen: fewer device backups (7 per person now).
- Delete `mizan_miniodata` once the owner agrees nothing in it is needed.

## Blockers
- None.

## Gotchas hit this session
- `curl -X HEAD` waits for a body and hangs; use `curl -I`.
- A synthetic `visibilitychange` locks the app.
- The other session keeps the main checkout on `feat/mobile-crypto-spike`.
