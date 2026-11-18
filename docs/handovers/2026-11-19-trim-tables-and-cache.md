# Handover — 2026-11-19 — Sync tables that stop growing; cached price reads

## Completed (3039d61 on develop and main)
- ADR sync-2; V026; `SyncHousekeeping`; identical-clock retries; per-op sync audit removed.
- Price cache (`CacheConfig`, `MarketCache`, `@Cacheable` reads, cleared after commit) and ETags
  on market GETs. Tests: SyncHousekeepingTest (3), market cache and 304 (3), snapshot retry.
  API 112 tests.

## In flight
- Nothing.

## Next steps
- Remaining from the analysis: Filebeat instead of Logstash, Kibana on demand, a "what changed?"
  signal so devices skip key calls.

## Blockers
- None.

## Gotchas hit this session
- Equal clocks used to count as a lost write; a resent op (sealed again) became a fake conflict.
- Caching inside a `@Transactional` method still borrows a connection on a hit; the cache advisor
  runs first (order 50) so a hit touches no database.
