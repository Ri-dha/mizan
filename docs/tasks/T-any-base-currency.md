# T-any-base-currency — Any main currency, country at sign-up, followed rates
- Status: in review
- Context: user request 2026-11-21; a base change re-expresses everything at the day's rate (user's choice). ADR currency-1.
- Notes: V030 (country, tracked currencies, base generations, `household_rebase`), `CurrencyCatalogue` and the public
  `/market/currencies`, `CurrencyChoices` validation at sign-up and in `PATCH /me`, `changeBaseCurrency` and `claimRebase`
  in `HouseholdService`, `WorldRateService.estimateFactor`; the shekel is no longer fetched. Clients in Ri-dha/MizanApp.
