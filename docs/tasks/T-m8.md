# T-m8 — Harden for release
- Status: done (2026-09-13, commit 7f21e3e)
- Context: NFR-01, NFR-06, NFR-08; docs/RUNBOOK.md.
- Notes: Property tests, lazy routes, axe checks, prod Compose with Caddy and nightly backups. Left open: CSP header, job locking for several instances (T-csp-and-job-lock), and the 1.5 s interactive target (T-perf-nfr01).
