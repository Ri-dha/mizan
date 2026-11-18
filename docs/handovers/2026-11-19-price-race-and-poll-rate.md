# Handover — 2026-11-19 — Price refresh race; IQWealth every 15 minutes

## Completed (uncommitted, MizanApi worktree ../.wt-price-race, branch fix/price-refresh-race)
- `market/service/PriceRecorder.java` (locked, own transaction), `MarketService` delegates to it
  and uses a compare-and-set cooldown; `PriceRecorderConcurrencyTest`; history test uses the recorder.
- `day-cron` `0 0/15 8-21 * * *` (56 by day + 3 at night = 59); comments, ARCHITECTURE, RUNBOOK.
- API 106 tests pass.

## In flight
- Nothing half-done. Awaiting the owner's word to commit.

## Next steps
- With 59 scheduled calls and a 40 reserve, a restart or a manual fetch can make the last
  evening poll skip; lower the reserve in the dashboard if that matters.

## Blockers
- None.

## Gotchas hit this session
- `refreshOnRequest` calls `refresh()` on itself, so its `@Transactional` never applied; the
  recorder's own transaction is what keeps each write atomic now.
