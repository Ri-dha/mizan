# T-iqwealth-gold-bid — Derive the dealer buying price below IQWealth gold
- Status: in-review (2026-11-20); MizanApi feat/iqwealth-gold-bid (worktree ../.wt-bid), MizanApp
  feat/iqwealth-gold-bid-setting (worktree ../.wt-bid-web), uncommitted
- Context: ADR prices-4; owner's choice after the buying price came out above the selling price.
- Notes: setting `MARKET_IQWEALTH_GOLD_BID_MARGIN_BP` (default 100), `IqWealthGoldFeed` answers
  `XAU_LOCAL_BID`, gold order covers it. API 120 tests. Live: 192,742 gram → 190,814.58 bid.
