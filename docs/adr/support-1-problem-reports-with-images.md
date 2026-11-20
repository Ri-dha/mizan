# ADR support-1 — Problem and feedback reports with images, readable by operators

- Date: 2026-11-21
- Status: accepted
- Task: T-support-reports

## Context
People need a way to tell the people who run Mizan about a problem, an idea or a question, with
screenshots. Everything else a person stores is end-to-end encrypted (ADR e2e-1), but operators
cannot help with a report they cannot read. The user chose problem and feedback reports with a
two-way conversation and a status over abuse reports or personal financial reports.

## Decision
- A report (`support_report`) belongs to one account, not a household, so it has no row-level
  security; the service checks the owner on every call. It has a category (problem, suggestion,
  question), a subject, a status (open, in progress, resolved) and a conversation of messages
  (`support_message`), each with up to four images (`support_attachment`).
- Reports are stored readable and the app says so before sending. Operators see every report on
  the dashboard; people see only their own and see operator replies as "Mizan support".
- Images are JPEG or PNG only, at most 5 MB each and 8000 px a side, checked by their bytes. The
  server removes EXIF, XMP, IPTC and comment segments from JPEGs and text, EXIF and time chunks from
  PNGs without decoding them, so the small heap never holds a decoded image. Clients also shrink
  and re-encode images on the device, which drops the same data.
- Images go to object storage under `support/<report>/<image>` and are served through the API, not
  by presigned links, so the owner check runs on every view. Responses carry
  `Content-Security-Policy: default-src 'none'; sandbox` and `nosniff`.
- ADR admin-1 applies: reading needs the operator role; replying and changing the status need the
  password again. Both are audited. A reply sends a push to every device of the reporter after
  the transaction commits.
- A person can send five reports and thirty replies a day. A reply to a resolved report opens it
  again.
- When an account is purged, its report images are deleted from storage; the rows go with the
  account.

## Consequences
- Operators can read whatever people put in reports, including screenshots of their numbers.
- Report images are kept until the account is deleted; a retention rule can come later if storage
  grows.
- The phone app needs its own screens (Ri-dha/MizanApp).
