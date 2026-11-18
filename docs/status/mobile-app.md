# Status — mobile-app
- Branch: none yet. Plan approved 2026-11-18 (T-mobile-app, ADR mobile-1).
- Exists: the plan only. No code.
- **OCR is on hold until the end of T-mobile-app, to be decided then** (owner, 2026-11-18). Not
  built until then: live camera recognition, Apple Vision on iPhone, native document scanners,
  Arabic on Android (Tesseract). The receipt photo flow ships in parity without reading text.
  Already in place for when it resumes: the parsing step (`extractReceipt`) moves to
  `packages/core` in Phase 2; all OCR must run on the device (ADR e2e-1).
- Order: after T-shadcn-ui, before T-price-history and T-admin-dashboard. Scope: full parity,
  iPhone and Android together.
- Blockers: Phase 0 needs the owner to (1) ask for a commit of the uncommitted work and (2)
  install Xcode and Android Studio. Phase 2 waits for T-shadcn-ui to finish.
- Validated: nothing yet.
