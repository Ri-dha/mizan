# T-email-only — Sign up and sign in with an email only
- Status: done (2026-11-21, 5d9c04c on develop and main)
- Context: user decision 2026-11-21 ("register via email"; Google and Apple sign-in dropped). ADR auth-email-1.
- Notes: server in Ri-dha/mizan (`Identifier`, `EmailCodeSender`, `VerificationService` sends after
  commit). Web forms and i18n in Ri-dha/MizanApp; the mobile sign-in screens belong to the phone app work.
