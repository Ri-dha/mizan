# Status — T-log-retention

- Merged into develop as `561acf2` (branch `feat/log-retention`).
- Works: lifecycle policies for every index Mizan writes, compression and no replicas in the
  templates, a policy on indexes that already existed, Docker log rotation on all services.
- Validated against Elasticsearch 9.3.3 under a throwaway compose project: the setup wrote the
  three policies and the metrics template; a pre-existing `mizan-logs-2026.01.01` was attached
  and deleted with retention set to 0; the index Logstash then created came under
  `containers-logs-retention`. `docker compose config` is valid for the prod and containers
  profiles.
- Not run: Metricbeat (the `containers` profile) and the API's metrics export.
