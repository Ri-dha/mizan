# T-lean-backend — Make the API need less memory, CPU and database work
- Status: in-review (2026-11-19), branch perf/lean-backend (worktree ../.wt-lean), uncommitted
- Context: owner asked for an analysis of the backend's resource use (2026-11-19) and chose the
  quick batch: JVM settings, fewer database round trips, log and metric trims, a smaller jar.
  ADR obs-3. Not in this task, still open: Filebeat instead of Logstash and Kibana on demand,
  pruning `sync_log` / `sync_op_receipt`, the per-op `audit_entry` rows (owner to decide), caching
  the shared price reads, a "what changed?" signal so devices skip key calls on most syncs.
- Notes: Dockerfile `JAVA_OPTS` (serial GC, 192 MB heap, 160 MB metaspace, 64 MB code cache,
  exit on OOM) and compose `mem_limit` 640m; tenancy bound in one statement
  (`TenantSession.bind`, role via `set_config('role', …)`) instead of three or four; key members
  in two queries; prod pool 2–6; prod OpenAPI document off; Netty excluded from the AWS SDK;
  access-line levels and metric bands (ADR obs-3). Measured in the JRE image: 617 → 478 MB in use,
  jar 93 → 84 MB (172 → 156 libraries), start 6.4 → 5.1 s. API 106 tests pass.
