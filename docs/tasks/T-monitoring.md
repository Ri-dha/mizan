# T-monitoring — Metrics, alerts and uptime beside Elasticsearch logs
- Status: in-review (2026-11-21), branch feat/monitoring (worktree ../.wt-monitoring), uncommitted
- Context: ADR obs-5; owner's choice after comparing setups (keep fast full-text log search).
- Notes: `infra/monitoring/` (scrape config, alert rules, Alertmanager start script, Grafana
  provisioning and dashboard), compose services, API Prometheus registry and `IqWealthMetrics`,
  backup push, Metricbeat and the ES metrics path removed. Tested under a throwaway project: all
  7 targets up, 10 rules healthy, `TargetDown` fired 4.5 min after storage stopped and cleared 2 min
  after it returned, Grafana provisioned (3 sources healthy, dashboard, live queries), logs searchable
  from Grafana, Kuma up. API 123 tests. Not verifiable on this Mac: cAdvisor per-container series.
