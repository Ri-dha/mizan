# Mizan — team status

Long-lived facts only. Change in its own small commit when one of them changes.

- **Phase**: the BRD's planned scope (v1, v2, v3, Phase 4) is delivered, followed by owner
  additions: end-to-end encryption, the IQWealth dollar, shadcn/ui with light and dark, price
  history and the operator dashboard (all on `main` since d5099dc). Next planned: phone apps with
  Expo (T-mobile-app, ADR mobile-1; T-flutter-app abandoned).
- **Repository**: github.com/Ri-dha/mizan. Branches: `main` (released) and `develop` (testing;
  task branches start here). Local checkout `myWallet/Mizan`.
- **Tracker**: none yet; keys are slugs (see AGENTS.md).
- **Environments**: local development only. No production host has been deployed. Production is
  defined by the `prod` Compose profile and `docs/RUNBOOK.md`.
- **Schema**: Flyway V001–V023.
- **Integrations**: IQWealth (iraqsm.com) for the Baghdad and Central Bank dollar, free key,
  personal use only; Baghdad Bullion House prices (keyless, unofficial endpoint), gold-api.com,
  open.er-api.com, optional metals.dev, GoldAPI and Open Exchange Rates with keys; Web Push
  (VAPID); MinIO. No payment provider.
- **Conventions**: AGENTS.md and the gitignored CLAUDE.md. Commit only when asked; branch from
  `develop`, merge into `develop` to test, fast-forward `main` only when asked.
