# ADR mobile-1 — Phones with Expo, sharing one TypeScript core with the web app

- Date: 2026-11-18
- Status: accepted (2026-11-18)
- Task: T-mobile-app
- Supersedes: the Flutter direction in T-flutter-app

## Context
The owner wants iPhone and Android apps and plans OCR features later: live camera recognition and
Apple Vision on iPhone matter to the owner, so the choice must not rule them out. Since ADR e2e-1 the client holds the product: key derivation,
field sealing, sync, the work devices do after a sync, and all money maths. A second client that
differs by one byte from the web app locks people out of their records.

Options weighed:
- **Flutter**: excellent camera and native feel, but every piece of logic is rewritten in Dart,
  including a second encryption implementation. Its usual on-device OCR (Google ML Kit) has no
  Arabic script, so it brings no OCR advantage.
- **Capacitor**: wraps the web app with almost no new code, but live camera OCR would need a
  custom native screen per feature.
- **Expo (React Native)**: native screens and first-class live camera processing
  (react-native-vision-camera frame processors, Apple Vision on iOS), while the logic stays the
  TypeScript the web app already runs and tests.

Everything needed is free apart from the store fees (Apple $99 a year, Google $25 once). Expo
cloud builds (EAS) are optional; the free tier or local builds on the Mac are enough.

## Decision
- Phones are an Expo app in `mobile/`, in this repository.
- The repository becomes an npm workspace. Logic shared by web and mobile moves to
  `packages/core`, which has no browser or React Native APIs: `domain/`, receipt parsing, the
  vault's field format and key hierarchy, sync merge and clocks, device duties, translations,
  the generated API types and client.
- Platform differences sit behind small interfaces that each app implements:
  - **Storage**: the logic reads and writes through a storage interface; Dexie (IndexedDB) on the
    web, SQLite (`expo-sqlite`) on phones.
  - **Crypto**: WebCrypto on the web; `react-native-quick-crypto` on phones, used only through the
    vault's own functions.
  - **Key storage at rest**: the IndexedDB device key on the web; the iOS Keychain and Android
    Keystore (`expo-secure-store`) on phones.
  - **Lifecycle**: online and visibility events on the web; network and app-state events on phones.
- The field format, key derivation and grants get fixed cases in `shared/test-vectors/` before the
  mobile app writes its first record. Both apps must pass them.
- OCR runs on the device only, and is built last: the owner put it on hold until the rest of the
  app reaches parity, to be decided then. The candidates are Apple Vision (and VisionKit's live
  scanner) on iPhone, the native document scanner on Android, and Tesseract for Arabic where no
  native engine reads it. The step from text to total, date and shop stays in `packages/core`.
- The first release has full parity with the web app, on iPhone and Android together.
- Local data on phones is encrypted at rest with SQLCipher, its key in secure storage.
- Push on phones uses the platforms' own channels (APNs and FCM, via Expo's push service or
  directly). The server learns a device token, never notification content beyond the generic
  reminder text of ADR e2e-1.
- The UI follows the web's shadcn look (ADR ui-1) through a React Native port of the components.

## Consequences
- The web app is refactored first (workspace, storage interface, vectors) without changing what
  it does; its tests must stay green throughout.
- Every logic change is made once, in `packages/core`, and reaches web and phones together.
- Live Arabic OCR on Android is limited: no free, fast on-device engine reads Arabic there, so
  Android falls back to capture-then-read.
- The server needs one addition: registering and sending to native push tokens.
- Building for iPhone needs Xcode on the Mac and an Apple Developer account for devices and the
  App Store.
