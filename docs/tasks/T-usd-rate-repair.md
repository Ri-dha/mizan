# T-usd-rate-repair — Repair USD rows saved with the old rate meaning
- Status: proposed (2026-11-18)
- Context: ADR rates-1.
- Notes: Rows in USD written before commit 7cfe595 store dinars-per-dollar instead of per cent, so they show about 14.7 on edit. Either re-enter them by hand or write a one-off migration that divides fx_rate_micros by 100 where currency = 'USD' and the value is above a plausible per-cent ceiling, applied through sync so devices receive it.
