# ADR obs-2 — Logs and metrics are kept for a fixed number of days

- Date: 2026-11-18
- Status: accepted
- Task: T-log-retention

## Context
ADR obs-1 left retention manual (`setup.ilm.enabled: false`, no policies), so every daily index
and every container's Docker log file grew without limit. The owner wants the least storage
that still keeps Kibana. Options weighed: replacing the stack with Loki and Grafana or
VictoriaLogs (smaller, but loses the Kibana dashboard and setup already built), Docker logs only
(no search), or keeping Elasticsearch with limits. The owner chose the last.

## Decision
- A one-shot `elasticsearch-setup` service runs before Logstash and writes three index lifecycle
  policies with a delete phase only: `mizan-logs-retention` (14 days), `containers-logs-retention`
  (7) and `metrics-retention` (7, for `mizan-metrics-*` and `metricbeat-*`). Days come from
  `.env`. The indexes stay daily; age is counted from creation, so no rollover is needed.
- Every index template names its policy and sets `index.codec: best_compression` and no
  replicas. The script also attaches the policies to indexes created before they existed.
- The API's metrics index gets a template from the setup script, because Micrometer creates it
  itself; Metricbeat gets the same settings through `setup.template.settings`.
- Every compose service uses the json-file driver with three files of 10 MB.

## Consequences
- Disk use levels off at roughly the volume of 14 days of API logs plus 7 days of the rest.
- Anything older than its retention is gone; an investigation must start within that window.
- Raising a retention value later applies to the indexes still held, not to deleted ones.
- Elasticsearch, Kibana and Logstash still need about 2 GB of memory; replacing them remains an
  option if the host is small (Loki or VictoriaLogs, see the T-log-retention notes).
