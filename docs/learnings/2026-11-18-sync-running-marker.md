# 2026-11-18 — An early return in syncNow must still clear `running`
`syncNow()` keeps the in-flight promise in `running` and returns it to every caller until it is
cleared. A return before the `try … finally` (offline, keys not open yet) left it set, so no sync
ran again until a reload. The promise now clears `running` in its own `.finally`; keep any new
early exit inside it.
Source: T-admin-dashboard
