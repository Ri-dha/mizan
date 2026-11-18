# T-receipt-retention — Smaller receipt photos, deleted after thirty days
- Status: in-review (2026-11-18), branch feat/log-retention (worktree ../.wt-log-retention), uncommitted
- Context: ADR storage-2. Owner chose grey 1200 px photos and a one-month limit that the app
  states to people, out of the storage savings discussed on 2026-11-18.
- Notes: `web/src/attachments/images.ts` (grey, 1200 px, q 0.6), `store.ts` (kind on URLs,
  `photoExpired`, `dropExpiredPhotos` after each sync), quick-add notice and removed message
  (en, ar), manual ledger page; API `AttachmentKind`, `ReceiptPhotoExpiry` (03:00), kind query
  on both URL endpoints, generated types. Also fixed: `AttachmentObjectPurge.scheduled()` called
  its `@Transactional` method on itself, so the purge ran outside a transaction.
