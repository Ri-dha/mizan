# T-price-refresh-race — Two price refreshes at once must not fail
- Status: done (2026-11-19), 7728113 on develop and main
- Context: seen in the key-rotation browser check: `POST /market/refresh` → 500, duplicate key on
  `price_quote` (XAU), when a user's refresh and another landed together on an empty table.
- Notes: `MarketService` read then inserted the quote, the day's history row and the change tick.
  Writes now go through `PriceRecorder.store`, its own transaction (REQUIRES_NEW, so the lock is
  not held across other instruments' network calls) that first takes
  `pg_advisory_xact_lock(0x50524943, instrument.ordinal())`. The manual-refresh cooldown is an
  atomic compare-and-set. `PriceRecorderConcurrencyTest` (8 writers) fails without the lock with
  the same duplicate key and passes with it. Same branch: IQWealth day cron every 15 minutes
  (59 calls a day, owner's request).
