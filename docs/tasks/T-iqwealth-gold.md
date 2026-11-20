# T-iqwealth-gold — IQWealth as the main gold source, chosen in the dashboard
- Status: done (2026-11-20): MizanApi 53547b8, MizanApp 59019cb, on develop and main
- Context: owner's request 2026-11-20; ADR prices-3. gold-api.com stays as the last fallback.
- Notes: `IqWealthClient` (shared budget, extracted from the dollar feed), `IqWealthGoldFeed`,
  `StoredQuotes`, `GoldFeedOrder`, gold poll in `IqWealthPoller`, setting `MARKET_GOLD_PRIMARY`,
  `gold-cron`, reserve 30. MizanApp: setting label and hint (en, ar), regenerated types. Tests:
  API 119 (gold parse and shared budget, feed order); checked in the dashboard: the setting shows,
  asks for the password, saves and reads "baghdadbullionhouse.com".
