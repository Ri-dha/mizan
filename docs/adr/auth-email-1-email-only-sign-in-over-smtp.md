# ADR auth-email-1 — Sign-in by email only, codes sent over SMTP

- Date: 2026-11-21
- Status: accepted
- Task: T-email-only

## Context
Accounts could be opened with an email or an Iraqi phone number (FR-ACC-01). Phone numbers need
an SMS provider, which costs money per message and is unreliable for Iraqi numbers, and no
production code sender existed at all: only the dev and test logger. Google and Apple sign-in were
considered and dropped, because neither gives the device a secret to derive the encryption key
from (ADR e2e-1), so they would need a second passphrase or would break end-to-end encryption.

## Decision
- Register, sign in, password reset and granting an operator accept an email address only. A phone
  number gets `INVALID_IDENTIFIER`. Emails are trimmed and lower-cased exactly as before, because
  the client derives the key salt from the same normalisation (`normaliseIdentifier`, pinned by
  the shared test vectors).
- Codes go out through any SMTP service (`MIZAN_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD`,
  `_FROM`, `_STARTTLS`). The message is plain text in Arabic and English.
- Codes are sent after the transaction commits. A failed send is logged and counted
  (`mizan_mail_failed_total`, alert `MailFailing`) and does not undo the registration; the person
  asks for the code again.
- The sender is built from `mizan.mail.*`, not `spring.mail.*`, so Boot's mail health check never
  turns the API's health red when the provider is slow.
- Without a mail host, the dev and test profiles log codes as before. The prod profile has no
  sender then, so the API refuses to start.
- The `phone` column stays (no migration). Admin screens still show it for any old account.

## Consequences
- No SMS cost. Production must have SMTP settings before it starts.
- Phone-only accounts can no longer sign in; there were none.
- Mobile and web sign-in forms change to an email field (MizanApp).
