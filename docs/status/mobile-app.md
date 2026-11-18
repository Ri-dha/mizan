# Status — mobile-app
- Branch: none yet. Plan approved 2026-11-18 (T-mobile-app, ADR mobile-1).
- Exists: the plan only. No code.
- **OCR is on hold until the end of T-mobile-app, to be decided then** (owner, 2026-11-18). Not
  built until then: live camera recognition, Apple Vision on iPhone, native document scanners,
  Arabic on Android (Tesseract). The receipt photo flow ships in parity without reading text.
  Already in place for when it resumes: the parsing step (`extractReceipt`) moves to
  `packages/core` in Phase 2; all OCR must run on the device (ADR e2e-1).
- Order: planned after T-shadcn-ui and before T-price-history and T-admin-dashboard; all three
  were finished first (2026-11-18, main at d5099dc), so parity now includes the Prices screen and
  the dashboard. Scope: full parity, iPhone and Android together.
- Blockers: Phase 0 needs the owner to install Xcode and Android Studio. The web work is
  committed and T-shadcn-ui is done, so nothing else waits.
- Validated: nothing yet.
