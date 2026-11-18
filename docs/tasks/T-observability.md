# T-observability — Logs, statistics and performance in Kibana
- Status: done (2026-09-15, commit f778279; logback file in 01ab5e6)
- Context: ADR obs-1.
- Notes: `docker compose --profile observability up -d`. Metricbeat is optional (`--profile containers`) because its image would not pull (T-metricbeat).
