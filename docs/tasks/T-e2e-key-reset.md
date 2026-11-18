# T-e2e-key-reset — Let someone who lost both password and recovery code start over
- Status: in-review (2026-11-18), on develop via feat/e2e-key-reset; not on main yet
- Context: ADR e2e-2, ADR e2e-3 (the decision). Today `PUT /api/v1/keys/me` refuses a second key set, so a user stuck on
  the recovery screen has no way forward.
- Notes: Add an explicit "start over" that replaces the user key and key pair, deletes the
  user's grants, and makes their old private rows unreadable (delete them, with a clear warning).
  Shared keys then come back through the normal auto-grant from another member's device. A
  household owner with no other member who holds the key loses the shared rows too; say so.
  - Done (ADR e2e-3): `KeyResetService` with `GET /keys/me/reset-preview` and `POST /keys/me/reset`
    (confirm word RESET, `KEYS_RESET` audit); a sole holder is someone who holds the household key
    and nobody else does, so a dependent's reset never touches shared rows. Web: "I don't have the
    code" on the recovery screen with the counted warning and a typed word, a hint to use another
    signed-in device first, and `KeysReplacedError` so a person's other devices wipe their copy
    and ask for the password.
  - Validated: 4 integration tests; live run with two devices (start over removed 1 private and
    10 shared records, the household restarted with new keys, the second device wiped itself and
    reopened with the new password); API 81 tests in 21 suites, web 165.

