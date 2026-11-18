# ADR e2e-3 — Starting over without a recovery code

- Date: 2026-11-18
- Status: accepted
- Task: T-e2e-key-reset

## Context
After a password reset the new password cannot open the old user key; only the recovery code
can (ADR e2e-1). Someone who has lost both was stuck on the recovery screen for good, because
`PUT /keys/me` refuses a second key set. Nobody, the service included, can open their old keys.

## Decision
- A signed-in person may **start over**: their device creates a new user key, key pair and
  recovery code and sends them to `POST /api/v1/keys/me/reset` with the confirmation word
  `RESET`. The server, in one transaction:
  - replaces their key set;
  - deletes every key grant they held, in every household (they were wrapped to the old key);
  - deletes their **private** records in every household (sealed under the old user key);
  - for each household where **no other member holds the household key**, deletes the
    household's shared records and every grant of that household, so its owner's device starts
    version 1 again. Elsewhere shared records are kept and another member's device grants the
    keys to the new public key on its next sync.
  - records `KEYS_RESET` in the audit log with the counts.
- `GET /api/v1/keys/me/reset-preview` returns those counts first, so the warning states what
  will be lost.
- The recovery screen offers starting over only behind that warning, and first suggests opening
  Mizan on another device that is still signed in, where Settings can make a new recovery code.
- Another device of the same person that still holds the old keys notices the public key changed
  on its next sync, drops its keys and local data, and asks for the password again.

## Consequences
- No one is locked out for good; what cannot be decrypted is removed rather than left to break
  sync for everyone.
- A sole key holder loses the household's shared history too. The warning says so with numbers.
- Shared records the person created stay (they are sealed under the household key).
