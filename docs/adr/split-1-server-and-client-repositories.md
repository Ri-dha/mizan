# ADR split-1 — Server and clients in two repositories

- Date: 2026-11-18
- Status: accepted
- Task: T-split-repos

## Context
Until now one repository (`Ri-dha/mizan`) held the server, the web app, the shared test vectors,
the infrastructure and the docs. The owner wants the server on its own, and the clients (web
today, iPhone and Android from T-mobile-app) together. The web app and the phone app must share
the encryption, sync and money code byte for byte (ADR e2e-1, ADR mobile-1), so keeping both
clients in one repository lets them import one copy of it, without publishing a package or using
a submodule. A three-way split (server, web, mobile) was considered; it needs a published core
package and a release step for every change to it.

## Decision
- `Ri-dha/mizan`, cloned locally as `myWallet/MizanApi`: `api/`, `docker-compose.yml`,
  `infra/backup`, `infra/observability`, `docs/RUNBOOK.md`, and the server's docs. The production
  compose profile builds the web image from a sibling checkout of MizanApp
  (`MIZAN_APP_DIR`, default `../MizanApp`).
- `Ri-dha/MizanApp`, cloned locally as `myWallet/MizanApp`: `web/`, `shared/test-vectors/`,
  `docs/manual/`, `infra/caddy/` (part of the web image), `spikes/`, and from T-mobile-app
  Phase 2 `packages/core` and `mobile/`. History was carried over with `git filter-repo`, keeping
  only these paths.
- The API contract between them is the OpenAPI document; clients regenerate their types from a
  running API (`npm run api:types`).
- Docs written before the split are in both repositories as they were on 2026-11-18. From now on
  each repository records its own tasks, ADRs, learnings and handovers. A decision that binds both
  (the sync protocol, the vault format, an API change) is written in both in the same change.
- The monorepo's last state is the tag `monorepo-final` in `Ri-dha/mizan`.

## Consequences
- An API change that clients must follow becomes two commits, server first; the server stays
  backward compatible until both clients have moved.
- Client CI runs only the parts a change touches (web, mobile, core); a change to the shared core
  runs both apps.
- `ARCHITECTURE.md` exists in both and keeps a "Repositories" section; each repository details
  its own side.
