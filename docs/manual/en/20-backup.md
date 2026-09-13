---
title: "Cloud backup"
screen: /settings
summary: "A daily encrypted copy on the server, the backup passphrase, and restoring on a new device."
---
## Why a separate passphrase

Sync already keeps the server up to date, but a backup is a whole snapshot you can restore after a mistake, and it is encrypted so that nobody, including the service, can read it. The PIN cannot be used for this: its key never leaves the phone. So the backup has its own **passphrase**, which is never sent anywhere. Write it down; without it a backup cannot be opened.

## Turning it on

In Settings, press **Set a backup passphrase**. From then on Mizan uploads one encrypted copy a day after a sync, and keeps the last seven. **Back up now** adds one at any time.

## Restoring

The list under Cloud backup shows every backup with its date and size. Press **Restore**, enter the passphrase, and Mizan shows what the backup contains before writing anything. Restored rows sync like your own edits. On a new device, sign in first; the list of backups arrives with the first sync.

## What is inside

Everything the JSON export contains, compressed and encrypted with AES-256. The service stores bytes it cannot read.
