# ADR rates-1 — Exchange rates are stored per minor unit of the source currency

- Date: 2026-11-18
- Status: accepted
- Task: T-subscriptions

## Context
Amounts are stored in minor units (cents for USD), and base amounts are computed as amount ×
rate / 10^6. Sheets stored the typed rate (dinars per dollar) directly, so every USD amount was
converted 100 times too high.

## Decision
`fx_rate_micros` means base minor units per source minor unit, in micros: 1,470 IQD per USD is
stored as 14,700,000. `rateToMicros(typed, currency)` and `microsToRate(stored, currency)` in
`web/src/db/income.ts` are the only conversions; every sheet uses them.

## Consequences
USD figures are correct everywhere. Rows saved before the fix carry the old value and need
re-entering (`T-usd-rate-repair`). Any new client must use the same meaning.
