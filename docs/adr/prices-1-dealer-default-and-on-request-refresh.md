# ADR prices-1 — Baghdad Bullion House as the default price, fetched only on request

- Date: 2026-09-15
- Status: accepted
- Task: T-live-prices

## Context
World spot times a dollar rate misses the local market's spread. Baghdad Bullion House publishes
bid and ask per gram of pure gold and silver and a product list. The owner asked for prices to
refresh only on the Refresh button or a page load, not in the background.

## Decision
`BaghdadBullionHouseFeed` is the first local feed: ask → `XAU_LOCAL`/`XAG_LOCAL`, bid →
`*_LOCAL_BID`, groups → `market_catalogue`. Households default to price source LOCAL; buy-back
valuation uses the published bid. World feeds remain fallbacks. The scheduler only runs when
`MIZAN_MARKET_AUTO_REFRESH=true`; otherwise `POST /api/v1/market/refresh` (30 s cooldown) is called
by the app on load and on Refresh.

## Consequences
Values match what the dealer charges and pays. Prices can be hours old if nobody opens the app,
which the stale flag shows. The feed is an unofficial endpoint and may change shape; the parser
returns empty rather than failing.
