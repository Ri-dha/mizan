# ADR rates-2 — World currencies from Frankfurter, the dinar from Mizan's own dollar

- Date: 2026-11-21
- Status: accepted
- Task: T-world-rates

## Context
Mizan is built for Iraq, but people want to check other currencies too: the euro, the lira, the
rial, the Gulf currencies. It is also a first step towards people outside Iraq using it. The
existing feeds cover only the dollar against the dinar.

## Decision
- Frankfurter (api.frankfurter.dev v2) supplies daily rates per US dollar for every real currency
  (about 160). It needs no key; its crypto and metal entries are dropped.
- The server fetches every six hours (`MIZAN_WORLD_RATES_CRON`, "-" turns it off) and on
  start-up, asking again for the last five days because a central bank's rate can arrive late.
  An empty table is filled with a year of history in one request (about 4 MB).
- Rates live in `world_rate` (currency, day, rate per dollar as `numeric`), not in the
  `Instrument` enum: 160 instruments would not fit the quote, tick and override model, and these
  rates change once a day.
- Frankfurter's dinar and dollar are not stored. Clients convert through the dollar, pricing the
  dinar with the household's own rate (Baghdad market or Central Bank, or the user's price), so a
  converted amount matches the rest of the app.
- Reads are open to any signed-in user: `GET /api/v1/market/world-rates` and
  `GET /api/v1/market/world-rates/history?currency&from&to` (at most 1,830 days).

## Consequences
The Prices screen gets a converter, a currency list and a chart per currency, offline from the
device's copy. Accounts and amounts in other currencies, and a base currency other than the
dinar, are not part of this; they can use the same table later.
