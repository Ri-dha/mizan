# Handover — 2026-11-18 — Move into Mizan and set up project docs

## Completed
- Subscriptions card on Bills and the USD per-cent rate fix, commit 7cfe595 on main (pushed).
- Project moved from myWallet to myWallet/Mizan; Compose name pinned to `mizan`; volumes copied.
  Database verified (7 users, schema V20); web 156 tests and the API suite green.
- Knowledge system written on branch docs/project-knowledge (uncommitted): AGENTS.md,
  ARCHITECTURE.md, docs/status.md, docs/adr (10), docs/tasks (21), docs/status/project-docs.md,
  docs/learnings (13), this handover.

## In flight
- T-project-docs: written, awaiting review and a commit request.

## Next steps
- The owner mentioned a rework before a Flutter client; its scope is not yet defined. Start a task
  file for it before touching code.
- T-usd-rate-repair for rows saved before the rate fix.
- Remove the old mywallet_* containers and volumes once the owner is satisfied.

## Blockers
- Port 5432 is held by the tariffcalc project's Postgres; Mizan's database cannot start until it
  is stopped.

## Gotchas hit this session
- Moving the folder changes Docker's default project name and would start empty volumes.
