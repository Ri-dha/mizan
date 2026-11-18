# 2026-09-13 — Server totals across members need app.system
Under RLS a request only sees its household's shared rows and the caller's private rows. Server
work that must count everyone's rows (month-close snapshots, household net worth, reports, purge
jobs) must call `TenantSession.elevateToSystem()` and filter visibility itself where it reports.
Sessions without HOUSEHOLD_VIEW are bound with `app.restricted = on`.
Source: T-m0-m1, T-v3
