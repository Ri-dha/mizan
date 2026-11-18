# 2026-11-18 — fx_rate_micros is per minor unit
USD amounts are cents, so 1,470 IQD per dollar is stored as 14,700,000 micros, not
1,470,000,000. Always convert typed rates with `rateToMicros`/`microsToRate`. A USD edit screen
showing about 14.7 means the row predates the fix.
Source: T-subscriptions
