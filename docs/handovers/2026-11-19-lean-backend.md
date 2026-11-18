# Handover — 2026-11-19 — Leaner API (quick batch)

## Completed (uncommitted, MizanApi worktree ../.wt-lean, branch perf/lean-backend)
- See T-lean-backend: JVM settings and memory limit, one-statement tenancy, two-query members,
  smaller prod pool, prod OpenAPI off, Netty excluded, access-line levels and metric bands
  (ADR obs-3), RUNBOOK and ARCHITECTURE. API 106 tests; measured 617 → 478 MB.

## In flight
- Nothing half-done. Awaiting the owner's word to commit.

## Next steps
- Owner to decide on per-op audit rows (stop them, or keep with a retention period).
- Larger savings not yet started: Filebeat instead of Logstash, Kibana on demand, pruning
  `sync_log` and `sync_op_receipt`, cached price reads, a "what changed?" signal for devices.

## Blockers
- None.

## Gotchas hit this session
- macOS compresses idle memory, so `ps` RSS reads far too low; measure the jar in a Linux
  container (`eclipse-temurin:25-jre`) with `docker stats`.
- The actuator metrics endpoints need a login; use `docker stats` for memory.
