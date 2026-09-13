---
title: "Privacy and security"
screen: /settings
summary: "What is stored where, what is encrypted, and what the service can and cannot see."
---
- Your data lives on your device first and is synced to the service under your household. Rows marked private are only ever returned to the person who created them.
- Passwords are hashed with Argon2id. Sessions use short-lived tokens; a stolen refresh token that is replayed ends every session for the account.
- Receipt photos are encrypted on the device before upload. The service stores bytes it cannot read.
- Price feeds are asked for prices, never told what you hold.
- The service never sells or shares your data. Support does not sign in as you; if you need help, export your data and share the file yourself.
- Every sign-in, sync write, month close and deletion request is recorded in an audit log kept for 24 months.
