# 2026-09-15 — Elasticsearch mappings and foreign container logs
Indexing every container's JSON into one index fails: another container's `hub: "connected"`
had been mapped as boolean. API lines go to `mizan-logs-*` with a fixed template; everything else
to `containers-logs-*`. A template only applies to new indices, so delete an index after changing
its template. Logstash refuses to run as root unless `ALLOW_SUPERUSER=true`, and tailing
`/var/lib/docker/containers` must start at the end or it ingests every old container's history
(and its own warnings, which loop).
Source: T-observability
