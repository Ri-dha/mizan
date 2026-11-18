# T-csp-and-job-lock — Add a Content-Security-Policy and lock scheduled jobs
- Status: in-review (2026-11-18), branch feat/csp-and-job-lock from develop, uncommitted
- Context: T-m8; docs/RUNBOOK.md security notes; ADR csp-and-job-lock-1 (job lock), csp-and-job-lock-2 (CSP).
- Notes: Done: V024 `scheduled_job_lock`, `JobLock` around all eight scheduled jobs, IQWealth
  stored-quote fallback for instances that did not poll, `infra/caddy/security-headers.caddy`
  imported by Caddy and served by `vite preview`, `MIZAN_STORAGE_ORIGIN`. Found on the way: the
  relaxed-SIMD Tesseract core was never copied (OCR failed on current Chrome) and the OCR cache
  kept HTML fallbacks; both fixed. Follow-up: T-storage-public-endpoint.
