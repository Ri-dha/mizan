# T-price-history — Keep every price change and let people see it
- Status: done (2026-11-18), commit f30a780 on feat/e2e-prices-admin, fast-forwarded to main at d5099dc
- Context: owner request 2026-11-18. Builds on T-iqwealth-fx. Needs ADR prices-2 (tick storage,
  scheduled gold polling). After T-shadcn-ui so the screen is built in the new look.
- Decisions (owner, 2026-11-18): second, after T-shadcn-ui; dollar, gold and silver only. Other
  currencies are not part of this task (item 6 below stays out).
- Notes:
  - Today: `price_history` keeps one row per instrument per day and the last fetch of the day
    overwrites it; changes within a day are lost. Only the sell-or-hold card reads it.
  - Server:
    1. `price_tick` (instrument, observed_at, price_micros, source), append-only, written only
       when the price differs from the instrument's previous tick. Kept forever (a few thousand
       rows a year per instrument).
    2. `price_history` gains open, high, low and close for the day, filled from ticks.
    3. Gold and silver ticks need regular fetches: poll Baghdad Bullion House on a schedule in
       Baghdad trading hours (keyless, every 30 minutes, configurable), like the IQWealth dollar.
    4. API: `GET /market/history` gains `interval=day|week|month` with OHLC;
       `GET /market/changes?instrument&from&to` lists ticks (newest first, paged). Prices are
       public data, readable by any signed-in user; no household data is involved.
    5. Back-fill: keep what `price_history` already has; seed the last 7 days of local gold per
       mithqal from IQWealth `/gold` once. Dollar history before polling began needs IQWealth Pro.
    6. Optional: other currencies as world rate × market (or official) dollar, from open.er-api,
       which also ends valuing non-dollar accounts at par. Decide separately.
  - Web:
    1. A **Prices** screen: market dollar, official dollar and the gap, local gold (21K and 24K
       per mithqal), silver; ranges 1D, 1W, 1M, 3M, 1Y, All; a line chart (OHLC per day for long
       ranges) and a list of changes (time, from → to, change and %, source, credit line).
    2. A "Dollar today" card on Home with the day's change.
    3. Last series viewed cached on the device for offline viewing (not a synced table).
    4. Optional: a push when the market dollar moves more than a chosen % in a day (extends the
       public metal-move alert).
  - Tests: tick only on change, OHLC roll-up, scheduled gold poll budget, history paging.
  - Done (ADR prices-2): V022 (`price_tick`, daily open/high/low), tick on change in
    `MarketService.store`, `MarketPoller`, `PriceHistoryService` with week/month roll-ups and the
    paged changes endpoint, both open to every signed-in role. Web: `market/prices.ts` (cached
    for offline), `features/prices/PricesPage.tsx`, `DollarTodayCard` on Home, Prices in every
    role's navigation, manual page 21, shadcn `chart` and `toggle-group` written from the official
    registry by hand (the CLI pulled in the `cn` npm package and downgraded Recharts).
  - Not done: the gold back-fill (see ADR prices-2) and the optional dollar-move push.
  - Validated: 5 new integration tests (tick on change, OHLC, week from Saturday and month
    roll-ups, paging with previous prices, append-only, dependents and advisors can read); live
    run with real IQWealth and Baghdad Bullion House ticks plus seeded test days; screenshots in
    dark Arabic desktop and light English phone; API 70 tests, web 164.

