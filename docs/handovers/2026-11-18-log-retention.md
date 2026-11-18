# Handover — 2026-11-18 — Limit how long logs and metrics are kept

## Completed (561acf2 on develop)
- ADR obs-2; `infra/observability/elasticsearch/setup.sh`; `elasticsearch-setup` service that
  Logstash waits for; lifecycle and compression in both log templates and in `metricbeat.yml`;
  `x-logging` rotation on every compose service; `.env.example` retention variables; RUNBOOK and
  ARCHITECTURE updated.

## In flight
- Nothing. Committed as 561acf2 and merged into develop.

## Next steps
- Owner to choose among the other storage savings (receipts, server backups, device backups).
- T-storage-public-endpoint; SeaweedFS in place of MinIO was discussed and not yet decided.

## Blockers
- None.

## Gotchas hit this session
- Another session switched the main checkout to `feat/mobile-crypto-spike`; this work is in a
  separate worktree for that reason.
- Test Elastic changes with `docker compose -p <throwaway>` so the real `esdata` volume and other
  projects' containers are untouched; `indices.lifecycle.poll_interval` of 5s shows a delete at once.
