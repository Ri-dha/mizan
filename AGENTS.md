# AGENTS.md — how to work on Mizan

Read this first, then `ARCHITECTURE.md`, `docs/status.md`, the task you are on in `docs/tasks/`,
the newest file in `docs/handovers/`, and the ADRs in `docs/adr/` for the area you will touch.

## Tracker and keys

- No external tracker has been used so far. Work is keyed by a short slug (`m9`, `live-prices`,
  `subscriptions`), and that slug names the task file (`docs/tasks/T-<slug>.md`), its status file
  and its ADRs (`docs/adr/<slug>-<n>-title.md`).
- If GitHub Issues on `Ri-dha/mizan` are adopted, use `gh-<number>` as the key for new work.
- One task in progress at a time. Scope that grows becomes a new `proposed` task.

## Branches, commits, merges

- `develop` (from 2026-11-18) is the testing branch: branch each task from `develop`, merge it
  into `develop` to test, and fast-forward `main` from `develop` only when asked.
- Branch per task: `feat/<slug>`, `fix/<slug>`, `docs/<slug>`.
- **Never commit, push or merge unless the user asks in that request.** Permission does not carry
  over to the next request.
- When asked to commit on `main`, create the branch first, commit there, then fast-forward `main`
  (`git merge --ff-only`). Push `main` only when told to. Direct fast-forward merges are the way
  this project merges; there are no pull requests so far.
- End commit messages with the attribution line the session gives you.

## Checks (all must pass before calling work done)

```bash
cd api && ./mvnw -B test                     # JUnit, Testcontainers Postgres, jqwik
cd web && npm run typecheck && npm test && npm run build
cd web && npm audit --audit-level=high       # CI runs this too
```

CI (`.github/workflows/ci.yml`) runs exactly these on every push to `main`. The API suite needs
Docker; it starts its own Postgres and does not use port 5432.

## Code rules (summary; the full local guide is `CLAUDE.md`, which is gitignored)

- Code says what; comments only say why, and only where the code cannot. A requirement id
  (`BR-03`, `FR-TRX-07`) is a welcome one-line comment. Any longer "why" belongs in an ADR or a
  learning.
- Money is integer minor units with a currency. Never a float.
- Domain maths gets a case in `shared/test-vectors/` first. The server no longer calculates
  (ADR e2e-1); the vectors are the contract for every client (web now, Flutter later).
- The server must never receive a plain amount, name or note (ADR e2e-1, e2e-2). A new synced
  table is a `SyncTable` bean listing its field names. Its rows live in `vault_record`, so it
  needs no Flyway table. Every field
  except `visibility` and `deletedAt` is sealed by `web/src/vault/records.ts`. Never add a server
  feature that reads record values; do it on the device (`web/src/sync/duties.ts`).
- Never edit an applied migration; add the next `V0NN__`.
- Web writes to syncable tables only through `web/src/db/write.ts`.
- Every user-facing string in both `web/src/i18n/en.json` and `ar.json`. Manual pages exist in
  both `docs/manual/en` and `docs/manual/ar` (the build fails otherwise).
- Config never hardcoded: `application.yml` under `mizan.*` with `${MIZAN_X:default}`.

## Never commit

- `.env` or any real secret, keys or tokens (scan the staged diff before committing).
- `api/logs/`, `web/public/ocr/`, `web/*.pem`, `web/dist/`, `node_modules/`, `api/target/`.
- `CLAUDE.md` and `PLAN.md` are local and gitignored; durable facts go in this file,
  `ARCHITECTURE.md` and `docs/`.

## Local environment facts

- Project root is `myWallet/Mizan` (moved there on 2026-11-18). Compose project name is pinned to
  `mizan`, so volumes are `mizan_pgdata`, `mizan_seaweeddata`, `mizan_esdata`.
- Dev test accounts live only in the local database; their credentials are not recorded here.
