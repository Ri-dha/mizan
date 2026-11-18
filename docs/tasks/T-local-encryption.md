# T-local-encryption — Encrypt local rows and free-text columns
- Status: proposed (2026-11-18)
- Context: ADR lock-1 leaves financial rows in plaintext in IndexedDB and Postgres.
- Notes: Option 1: encrypt IndexedDB rows with the PIN-wrapped key. Option 3: encrypt payee, notes, dealer and serial per household on the server, since aggregates never read them. Full end-to-end encryption would break server snapshots, reports and notifications.
