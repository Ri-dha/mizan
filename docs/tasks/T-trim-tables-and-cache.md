# T-trim-tables-and-cache — Stop the sync tables growing; serve shared prices from memory
- Status: in-review (2026-11-19), branch perf/trim-tables-and-cache (worktree ../.wt-trim), uncommitted
- Context: items 1 and 3 of the 2026-11-19 resource analysis; owner chose to stop the per-op
  audit rows. ADR sync-2.
- Notes: `SyncHousekeeping` job and V026 indexes; `FieldMerger` skips identical clocks and
  `SyncService` acknowledges an append-only duplicate; per-op `SYNC_INSERTED`/`SYNC_UPDATED` audit
  removed. `CacheConfig` (`@EnableCaching`, order 50, outside the transaction advisor),
  `MarketCache` (three caches, cleared after commit by `PriceRecorder.store` and tick deletion,
  60 s expiry, 500 entries); quotes, history and changes are `@Cacheable`; market GETs send
  `Cache-Control: private, no-cache` and an ETag (`MarketHttpConfig`), so an unchanged answer is
  a 304. API 112 tests.
