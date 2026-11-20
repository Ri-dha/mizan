# ADR obs-4 — Filebeat ships the logs; Kibana runs on demand

- Date: 2026-11-20
- Status: accepted (replaces Logstash from ADR obs-1)
- Task: T-filebeat

## Context
Logstash only tailed container log files and the local API file, parsed JSON lines, dropped
its own warnings and routed API lines to `mizan-logs-*` and the rest to `containers-logs-*`.
Measured on 2026-11-20: Logstash 566 MB in use (1.45 GB image), Filebeat doing the same 48 MB
(564 MB image). Kibana used 1.2 GB while nobody was looking. The owner chose Filebeat and an
own profile for Kibana.

## Decision
- Filebeat 9.3 (`infra/observability/filebeat.yml`) reads `/var/lib/docker/containers/*/*-json.log`
  (not Docker's binary `container-cached.log`) and `api/logs/*.json`, decodes JSON lines into
  top-level fields, takes `container_id` from the path, drops Filebeat's own lines and its
  metadata fields, and writes `mizan-logs-*` when `application` is `mizan`, else
  `containers-logs-*`. Files are identified by inode, so a short log is read at once (the default
  fingerprint waits for 1 KB). Files idle for a day are skipped.
- `elasticsearch-setup` installs both index templates; Filebeat's own templates and lifecycle are
  off. The documents look as they did, so the dashboard and data views are unchanged.
- Kibana and `kibana-setup` move to the `kibana` profile; `observability` and `prod` no longer start them.

## Consequences
- About 0.5 GB less in use for log shipping, and 1.2 GB more whenever Kibana is not running.
- Kibana needs `--profile kibana`; `docker compose --profile prod up -d` alone leaves it off.
- A first start reads up to a day of each running container's existing log (capped by Docker at 30 MB).
