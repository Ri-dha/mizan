# ADR lock-1 — PIN-wrapped data key, WebAuthn PRF, receipts encrypted on the device

- Date: 2026-09-13
- Status: accepted
- Task: T-m3-m4

## Context
Phones are shared and lost; receipt photos are sensitive and stored on a server the user may not
control. The browser offers WebCrypto and WebAuthn but only on a secure origin.

## Decision
A random AES key is generated per device and wrapped with a PBKDF2 key from the PIN, and
optionally with a WebAuthn PRF secret for biometrics. Receipts are AES-GCM encrypted with it
before upload through presigned URLs; the server holds ciphertext only. Backups use a separate
passphrase-derived key.

## Consequences
The server cannot read receipts or backups. A forgotten PIN cannot be recovered on that device.
Everything needs HTTPS, including LAN testing. Financial rows are not encrypted by this.
