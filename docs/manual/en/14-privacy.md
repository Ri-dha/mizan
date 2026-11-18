---
title: "Privacy and security"
screen: /settings
summary: "What is stored where, what is encrypted, and what the service can and cannot see."
---
- Your data lives on your device first and is synced to the service under your household. Rows marked private are only ever returned to the person who created them.
- **Everything is encrypted on your device before it syncs.** Amounts, names, notes, dates, plans and receipts reach the service as ciphertext. The service stores them, syncs them and keeps them safe, but it cannot read them, and neither can anyone who copies its database or backups. It can see only how many records you have, when they changed, and whether each is shared or private.
- Shared records are encrypted with a household key that every member's device holds. Private records are encrypted with your own key. Advisors get a separate key that opens only the annual report.
- Your password never leaves your device. The service receives a key derived from it, which it cannot turn back into the password.
- **Your recovery code is the only way back if you forget your password.** It is shown once, when your account is created or upgraded. Write it down and keep it somewhere safe. A password reset by email or SMS lets you sign in again, but your records stay locked until you enter the code. Lost both? Nobody can open your records, not even us. You can make a new code in Settings at any time.
- Sessions use short-lived tokens; a stolen refresh token that is replayed ends every session for the account.
- On the device itself your records are kept unencrypted inside the browser's storage, protected by the device lock and Mizan's PIN screen.
- Price feeds are asked for prices, never told what you hold.
- The service never sells or shares your data. Support does not sign in as you; if you need help, export your data and share the file yourself.
- Every sign-in, sync write and deletion request is recorded in an audit log kept for 24 months. A sync write is logged by which fields changed, never by their values.
