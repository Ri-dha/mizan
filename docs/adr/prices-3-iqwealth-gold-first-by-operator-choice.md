# ADR prices-3 — IQWealth gold first, by the operator's choice

- Date: 2026-11-20
- Status: accepted
- Task: T-iqwealth-gold

## Context
Gold came from Baghdad Bullion House (local dealer, bid and ask per gram) and world feeds
(metals.dev, GoldAPI.io when keyed, gold-api.com keyless). The owner asked for IQWealth's free
`GET /gold` to be the main gold source, the others as fallbacks, and for the choice to be
changeable in the operator dashboard. A real answer (2026-11-20) gives the gram by karat (24,
22, 21, 18, 14) in dinars and dollars, the ounce buy and sell in dollars, yesterday's prices and
a week of daily mithqal prices. Its 24-karat gram equals the world price times the market
dollar: no dealer premium, no dealer bid, no silver. It is published once a day. The free key's
100 requests a day are shared with the dollar polling (59 a day).

## Decision
- `IqWealthGoldFeed` answers `XAU_LOCAL` (the 24-karat gram, IQD per gram of pure metal) and `XAU`
  (the middle of the ounce's buy and sell, USD) from its last poll, four times a day
  (`gold-cron`, 08:05, 12:05, 16:05, 20:05 Baghdad) and once at start-up, under its own job lock.
- Both IQWealth feeds go through one `IqWealthClient`, which holds the day's count, the reserve,
  the midnight pause after a 429 and the refused key, so gold and the dollar spend one budget.
  The default reserve drops from 40 to 30 so 63 scheduled calls fit.
- The setting `MARKET_GOLD_PRIMARY` (`iraqsm.com` by default, or `baghdadbullionhouse.com`) puts
  IQWealth first for `XAU` and `XAU_LOCAL`; every other feed keeps its order behind it. Otherwise
  IQWealth sits after the dealers and before gold-api.com.
- The dealer bid (`XAU_LOCAL_BID`) and silver keep their current sources.

## Consequences
- With IQWealth first, local gold is the world price in dinars: lower than a dealer's selling
  price, and the dealer's buying price still comes from Baghdad Bullion House, so the two can
  disagree slightly. Operators can switch back in the dashboard without a restart.
- A restart spends one more request; the operator's "Fetch now" spends two (dollar and gold).
