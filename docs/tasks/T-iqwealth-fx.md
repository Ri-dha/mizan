# T-iqwealth-fx — Take the Baghdad market dollar from IQWealth
- Status: in-review (2026-11-18), uncommitted on branch docs/project-knowledge
- Context: the market dollar had no feed (operator setting or user override only). The owner chose
  IQWealth's free key, spread over the day by a background job. ARCHITECTURE.md "Market data".
- Notes: `market/feed/IqWealthFxFeed.java` (cache, budget, 429 and 401/403 handling),
  `market/service/IqWealthPoller.java` (start-up, day and night crons in Asia/Baghdad),
  `MarketService.refresh(Set)`, settings under `mizan.market.feeds.iq-wealth`, web
  `components/SourceCredit.tsx`. Only `/fx` is used: `/gold` per gram is spot × the market dollar,
  which Mizan already computes, and its Iraqi wholesale figures were 11 days old when checked.
  The free licence is personal and non-commercial; switch to the Business key before going public.
  Validated: 6 unit tests with a mocked server; a live start-up poll stored 1,702 and 1,510 from
  iraqsm.com, and a user's Refresh kept them.
