# T-flutter-app — Build a Flutter client
- Status: abandoned (2026-11-18): superseded by T-mobile-app and ADR mobile-1. After ADR e2e-1 a
  Dart client would need a second encryption implementation, and Flutter's usual OCR has no Arabic.
- Context: user's planned rework; ADR foundation-1, sync-1, rates-1, lock-1.
- Notes: Needs a Dart sync engine with the same per-field merge, a port of web/src/domain driven by shared/test-vectors, drift for storage, local_auth and secure storage for the lock, byte-compatible AES-GCM receipts, FCM push sender on the server, refresh token in the body. Estimate four to six weeks for the core loop. Capacitor wrapping is the cheaper alternative.
