# T-mobile-app — iPhone and Android apps with Expo
- Status: proposed (2026-11-18), plan approved; not started
- Context: ADR mobile-1 (accepted), ADR e2e-1, e2e-2, sync-1, ui-1. Supersedes T-flutter-app.
  Owner decisions 2026-11-18: after T-shadcn-ui and before T-price-history and T-admin-dashboard;
  full parity in the first release; iPhone and Android together; **OCR on hold until the end**
  (see `docs/status/mobile-app.md`).
- Notes (phases; effort about 9–12 weeks before OCR):
  0. Owner: commit the uncommitted work on separate branches; install Xcode and Android Studio
     (Apple Developer $99/year for real iPhones, Google Play $25 at release).
  1. Spike (1–2 days): prove `react-native-quick-crypto` `subtle` matches the web byte for byte
     for PBKDF2-SHA256 600k (timed on a mid-range Android), HKDF, AES-GCM with AAD, AES-KW, ECDH
     P-256, import/export raw, spki, pkcs8, jwk, SHA-256. Gaps: `@noble/*`, but PBKDF2 stays
     native. Write a learning with the result.
  2. Shared core: npm workspaces; `packages/core` (no React, no browser or RN globals). Move
     `domain/`, receipt parsing, `sync/hlc.ts`, `sync/merge.ts`, `vault/crypto.ts`,
     `vault/records.ts`, schema types, translations, API types. Platform interfaces: `Store`
     (tables, transactions, change events, meta, outbox, conflicts, sealed inbox; web adapter
     wraps Dexie), `Crypto` (SubtleCrypto), `SecretStore`, `Lifecycle`. Then move `db/*.ts`,
     `db/write.ts`, `db/householdFigure.ts`, `sync/engine.ts`, `sync/duties.ts`,
     `vault/keyring.ts`, `vault/account.ts`, `market/store.ts` logic, `lock/crypto.ts`,
     `attachments/crypto.ts`, the backup format, and the logic of `useMonthFigures`,
     `useMetals`, `useNetWorth`. Web tests green after every step. ADR mobile-2 for the
     interfaces.
  3. Vectors: `shared/test-vectors/vault-*.json` (identifier normalisation, auth key and wrap
     secret, account key wrap/open, grant wrap/open, field seal/open, deterministic ids, blob
     keys), generated from the web with fixed randomness; core and mobile run them.
  4. Mobile foundation: Expo with Expo Router and development builds; adapters over `expo-sqlite`
     with SQLCipher, quick-crypto, `expo-secure-store`, AppState and NetInfo; `openapi-fetch`
     with the refresh token in secure storage; NativeWind and react-native-reusables (shadcn
     look), light/dark, Arabic RTL; PIN plus `expo-local-authentication`.
  5. Parity, in the web app's order: auth and vault screens, sync, home, ledger and quick add,
     plan, income, bills, debts, goals, cash, gold and silver, assets, net worth and month close,
     reports, household and join links, advisor, settings (theme, language, privacy, backup,
     notifications, CSV via share sheet), help, tours, receipt photos (no OCR), charts.
  6. Push and release: `PushSubscription` channel and token, `ExpoPushSender` beside
     `WebPushSender`, a Flyway migration; mobile registers its token and uploads the reminder
     schedule; icons, store listings in English and Arabic, TestFlight and Play internal testing.
  7. OCR: on hold; decided at the end.
- Done means: the mobile app passes every vector, syncs both ways with the web in one household,
  opens records sealed by the web and vice versa, and matches the household figure and month
  close to the dinar, in light and dark, English and Arabic.
