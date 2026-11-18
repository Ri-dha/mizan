# ADR storage-2 — Receipt photos are small and last thirty days

- Date: 2026-11-18
- Status: accepted
- Task: T-receipt-retention

## Context
The owner wants the least storage possible. Receipt photos were the largest thing Mizan keeps
per person: colour JPEG at 1600 px, quality 0.82, about 200–400 KB each, kept until deleted,
both in object storage and on every device that opened them. The server cannot tell a receipt
from a device backup, since every attachment field is sealed (ADR e2e-1), and cannot shrink an
image it cannot read.

## Decision
- Devices shrink receipt photos to 1200 px on the long edge, convert them to grey on the pixels
  (Safari ignores the canvas `filter`), and save JPEG at quality 0.6. A phone-sized test image
  went from 199 KB to 65 KB; OCR still read it.
- When asking for an upload or download URL the device says the kind, `RECEIPT` or `BACKUP`
  (default `BACKUP`, which keeps older clients' keys). Receipts are stored under
  `receipts/<household>/<id>`, backups under `<household>/<id>`. That kind is the only new thing
  the server learns.
- `ReceiptPhotoExpiry` deletes objects under `receipts/` whose upload is older than
  `mizan.storage.receipt-retention` (30 days), judged by the store's own timestamp, under the
  job lock. Devices drop their local copy on the same rule, counted from `uploadedAt`, after each
  sync. The attachment row stays, so the transaction keeps everything typed.
- The quick-add sheet says photos are kept for 30 days, and says so again when one is gone; the
  manual's ledger page states the rule in English and Arabic.

## Consequences
- Receipt storage is bounded by a month of photos per household, at about a third of the size.
- After 30 days a receipt cannot be shown or read again; people must copy what they need first.
- The number lives in two places (`application.yml`, `web/src/attachments/store.ts`) and in the
  manual; changing it means changing all three.
- A photo never uploaded (device always offline) does not start its month until it uploads.
