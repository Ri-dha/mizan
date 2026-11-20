# ADR prices-4 — The dealer buying price is derived below IQWealth gold

- Date: 2026-11-20
- Status: accepted (extends ADR prices-3)
- Task: T-iqwealth-gold-bid

## Context
With IQWealth first for gold (ADR prices-3), the selling price per gram came from IQWealth (the
world price in dinars) while the buying price still came from Baghdad Bullion House. On
2026-11-20 the dealer's buying price (194,240) was above IQWealth's gram (193,291), so selling
gold to a dealer looked worth more than the gold itself. IQWealth publishes no buying price.
The owner chose to derive it from IQWealth minus a margin.

## Decision
- `IqWealthGoldFeed` also answers `XAU_LOCAL_BID`: its 24-karat gram less
  `MARKET_IQWEALTH_GOLD_BID_MARGIN_BP` basis points (0 to 2,000; default 100, 1%), changeable in
  the dashboard. It is worked out on each lookup, so a new margin applies at the next refresh
  with no IQWealth request.
- The buying price follows the same main-source choice as the rest of gold: IQWealth first when
  `MARKET_GOLD_PRIMARY` is `iraqsm.com`, Baghdad Bullion House's real buying price otherwise.

## Consequences
- Selling and buying prices come from one source and the buying price is always the lower.
- The margin is a guess until compared with real dealer quotes; operators should tune it.
