# ADR obs-5 — Metrics and alerts in VictoriaMetrics, Grafana and Uptime Kuma; logs stay in Elasticsearch

- Date: 2026-11-21
- Status: accepted (moves metrics out of ADR obs-1's Elasticsearch)
- Task: T-monitoring

## Context
The API pushed metrics to Elasticsearch, Metricbeat added container figures, and nothing alerted:
an outage, a full disk or a refused IQWealth key went unnoticed until someone looked. Postgres and
SeaweedFS had no metrics. The owner compared Prometheus + Loki + Grafana, an all-Victoria stack and
a hybrid, required fast free-text search over all logs to stay, and chose Elasticsearch for logs
with VictoriaMetrics for metrics and alerts, Grafana and Uptime Kuma.

## Decision
- The API serves `/actuator/prometheus` (Micrometer Prometheus registry; open without a login,
  as the API port is published on 127.0.0.1 only and Caddy forwards no other actuator path) with
  IQWealth quota gauges added (`mizan_iqwealth_*`). The Elasticsearch metrics export and Metricbeat
  are removed; old metric indexes age out.
- VictoriaMetrics scrapes the API, postgres-exporter, SeaweedFS (`-metricsPort=9327`),
  node-exporter and cAdvisor every 30 s, keeps 30 days and caps its caches at 96 MB.
- vmalert evaluates the rules in `infra/monitoring/vmalert/rules.yml`, writes `ALERTS` back, and
  Alertmanager sends to Telegram when a bot token and chat id are set, otherwise to nobody.
- Grafana (provisioned data sources: VictoriaMetrics, the `mizan-logs-*` Elasticsearch indexes,
  Alertmanager; dashboard "Mizan metrics") with the bundled plugins skipped.
- Uptime Kuma checks availability, certificates and the backup, which calls a push URL after a
  good dump. Its monitors are created in its UI (it has no file provisioning).
- All are in `observability` and `prod`, UIs on 127.0.0.1. Kibana stays for logs, on demand.

## Consequences
- Alerts exist. About 590 MB more in use (measured), against ~100 MB of Metricbeat removed.
- Two systems to look after (Elasticsearch for logs, VictoriaMetrics for metrics).
- Kibana's dashboard lost its four metric panels; they are in Grafana now.
- Kuma shares the host, so a dead server needs an external check to be noticed.
