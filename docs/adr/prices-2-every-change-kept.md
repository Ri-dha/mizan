# ADR prices-2 — Every price change is kept, and prices are polled on a schedule

- Date: 2026-11-18
- Status: accepted
- Task: T-price-history
- Extends: ADR prices-1 (fetch on request) and T-iqwealth-fx (scheduled dollar)

## Context
`price_history` kept one row per instrument per day and the last fetch overwrote it, so moves
within a day were lost, and prices were only fetched when someone opened the app. The owner
wants the history of every price change visible to people.

## Decision
- `price_tick` records a price only when it differs from the instrument's previous tick. It is
  append-only (a trigger refuses updates) and kept forever. Its time is the source's quote time.
- `price_history` keeps the day's close and gains its open, high and low, updated on each fetch.
- `MarketPoller` refreshes every feed on `mizan.market.poll-cron` (every 30 minutes, 07:00–22:00
  Baghdad time); the IQWealth dollar keeps its own budgeted schedule and is served from cache.
- `GET /market/history` rolls days up by `DAY`, `WEEK` (weeks start on Saturday, as in Iraq) or
  `MONTH`; `GET /market/changes` lists ticks newest first with the price each replaced, paged by
  `before`. Both are open to any signed-in user, dependents and advisors included: prices are
  public data and hold nothing of any household.
- No back-fill: IQWealth's 7-day gold history is spot × the market dollar rather than a dealer
  price, so it would mix two kinds of number in `XAU_LOCAL`; dollar history before polling began
  needs IQWealth Pro.

## Consequences
- Charts of a day or a week show every change; longer ranges show daily, weekly or monthly closes.
- With a keyed spot provider configured, each poll counts against its quota; fit the cron to it.
- Admin corrections (T-admin-dashboard) add a correcting tick or delete a bad one; they never edit.
