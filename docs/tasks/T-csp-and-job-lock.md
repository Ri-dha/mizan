# T-csp-and-job-lock — Add a Content-Security-Policy and lock scheduled jobs
- Status: proposed (2026-09-13)
- Context: T-m8; docs/RUNBOOK.md security notes.
- Notes: Caddy sends no CSP yet. Scheduled jobs (purges, month close, notifications) would run on every instance if the API is scaled out; a database advisory lock or ShedLock is needed first.
