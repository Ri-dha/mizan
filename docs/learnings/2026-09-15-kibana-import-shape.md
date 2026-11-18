# 2026-09-15 — Importing Lens saved objects
Kibana 9 rejects Lens objects with `references` inside `attributes`, and needs
`coreMigrationVersion`/`typeMigrationVersion`. Keep references at the top level of each NDJSON
object, as in `infra/observability/kibana/mizan-dashboard.ndjson`.
Source: T-observability
