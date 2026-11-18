# Status — T-csp-and-job-lock

- Merged into develop as `e15b0eb` (branch `feat/csp-and-job-lock`); main not yet promoted.
- Works: every scheduled job takes a row in `scheduled_job_lock` (V024) through `JobLock` before
  running; the IQWealth feed answers from the stored quote on instances that did not poll; Caddy
  and `vite preview` send the same CSP from `infra/caddy/security-headers.caddy`.
- Also fixed: the relaxed-SIMD OCR core is copied into `public/ocr`; the service worker refuses
  to cache an HTML answer for `/ocr/*`.
- Not checked: Caddy parsing the new `import` (no Caddy image on this machine). Run
  `docker compose --profile prod build web` and start it before deploying.
- Validated: API 88 tests (6 new in `JobLockIntegrationTest`, 1 new in `IqWealthFxFeedTest`),
  web 168 tests (3 in `securityHeaders.test.ts`) and `tsc -b`. In `vite preview` with the real
  policy, against the API and MinIO, there were no violations through register, PIN, home,
  receipt upload to MinIO and OCR (read 25,000, SUPERMARKET, Groceries).
- Next: T-storage-public-endpoint (presigned URLs name `http://minio:9000` in prod).
