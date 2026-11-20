# T-filebeat — Ship logs with Filebeat and run Kibana on demand
- Status: in-review (2026-11-20), branch feat/filebeat (worktree ../.wt-filebeat), uncommitted
- Context: ADR obs-4; item 1 of the remaining resource work, owner's choice.
- Notes: `filebeat.yml`, compose `filebeat` service and `filebeatdata` volume, templates moved to
  `infra/observability/elasticsearch/` and installed by `setup.sh`, `logstash.conf` removed,
  Kibana on the `kibana` profile. Tested under a throwaway compose project: API JSON from a
  container and from `api/logs/mizan.json` lands in `mizan-logs-*` with the same fields as
  before, other output in `containers-logs-*` with `stream` and `container_id`, no parse errors,
  no duplicates on a clean run; Kibana starts on its profile and imports 4 data views and the
  dashboard (11 objects). Measured: Filebeat 48 MB vs Logstash 566 MB; Kibana 1.2 GB.
