# T-log-retention — Keep logs and metrics for a fixed number of days
- Status: in-review (2026-11-18), branch feat/log-retention from develop 588eddf, uncommitted
- Context: ADR obs-2 (supersedes the manual retention in ADR obs-1's runbook notes). Owner asked
  for the least storage possible and chose to keep Elasticsearch with limits over Loki + Grafana,
  VictoriaLogs or Docker logs only.
- Notes: `infra/observability/elasticsearch/setup.sh`, the `elasticsearch-setup` service,
  templates with policy and `best_compression`, Metricbeat template settings, `x-logging` on
  every service. Other storage savings discussed but not yet chosen: grayscale 1200 px receipts
  (about 4x smaller), server backups 7 daily + 4 weekly with `pg_dump -Fc`, fewer device backups.
