# Status — T-seaweedfs, T-backup-retention and T-receipt-retention

- Merged into develop: `6b0c2d4` SeaweedFS, `d8a987e` backups, `fde03d9` receipts (branch `feat/log-retention`).
- Works: SeaweedFS 4.51 in compose; the API creates its bucket, signs URLs and uploads land under
  `receipts/`; the backup image writes a custom-format dump compressed with zstd (37.8 KB), keeps 7 daily and
  4 weekly, and the dump restores into a scratch database that matches the live one. Receipt photos are grey
  1200 px (199 KB → 65 KB on a test image, OCR still reads it), deleted from storage after 30
  days and from devices on the same rule, with the notice and the removed message shown.
- Validated: API 89 tests (`ReceiptPhotoExpiryTest` against a SeaweedFS container: kept at 29
  days, deleted at 31, backups untouched), web 171 tests and `tsc -b`; in `vite preview` with the
  CSP, against this API and SeaweedFS: no violations, upload, OCR, backdated expiry and message.
- Not run: the full prod profile (Caddy, the `web` image); T-storage-public-endpoint is still open.
- Data: the old `mizan_miniodata` volume is untouched; dev rows from this test point at objects
  in a removed test volume.
