# T-e2e-key-reset — Let someone who lost both password and recovery code start over
- Status: proposed (2026-11-18)
- Context: ADR e2e-2. Today `PUT /api/v1/keys/me` refuses a second key set, so a user stuck on
  the recovery screen has no way forward.
- Notes: Add an explicit "start over" that replaces the user key and key pair, deletes the
  user's grants, and makes their old private rows unreadable (delete them, with a clear warning).
  Shared keys then come back through the normal auto-grant from another member's device. A
  household owner with no other member who holds the key loses the shared rows too; say so.
