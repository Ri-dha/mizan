# Mizan — team status

Long-lived facts only. Change in its own small commit when one of them changes.

- **Phase**: the BRD's planned scope (v1, v2, v3, Phase 4) is delivered and on `main`. Work since
  then is owner-requested additions. A Flutter client is proposed (T-flutter-app).
- **Repository**: github.com/Ri-dha/mizan, branch `main`. Local checkout `myWallet/Mizan`.
- **Tracker**: none yet; keys are slugs (see AGENTS.md).
- **Environments**: local development only. No production host has been deployed. Production is
  defined by the `prod` Compose profile and `docs/RUNBOOK.md`.
- **Schema**: Flyway V001–V020.
- **Integrations**: Baghdad Bullion House prices (keyless, unofficial endpoint), gold-api.com,
  open.er-api.com, optional metals.dev, GoldAPI and Open Exchange Rates with keys; Web Push
  (VAPID); MinIO. No payment provider.
- **Conventions**: AGENTS.md and the gitignored CLAUDE.md. Commit only when asked; branch, then
  fast-forward main.
