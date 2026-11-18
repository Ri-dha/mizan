# ADR csp-and-job-lock-2 — A Content-Security-Policy on the web app

- Date: 2026-11-18
- Status: accepted
- Task: T-csp-and-job-lock

## Context
Caddy sent no CSP. The app now holds encryption keys in the browser (ADR e2e-1), so injected
script is the main way left to read someone's data. The built page has no inline script (the
theme is set by `public/theme-boot.js` for this reason, ADR ui-1); on-device OCR compiles
WebAssembly from `/ocr`; receipts and exports use `blob:` URLs; receipts move between the
browser and object storage through presigned URLs.

## Decision
- Caddy sends, from `infra/caddy/security-headers.caddy`:
  `default-src 'self'`; `script-src 'self' 'wasm-unsafe-eval'`; `style-src 'self'
  'unsafe-inline'`; `img-src 'self' data: blob:` plus the storage origin; `connect-src 'self'`
  plus the storage origin; `font-src 'self' data:`; `worker-src 'self'`; `manifest-src 'self'`;
  `object-src 'none'`; `base-uri 'self'`; `form-action 'self'`; `frame-ancestors 'none'`.
- The storage origin comes from `MIZAN_STORAGE_ORIGIN` (empty when storage is behind the same
  origin).
- Styles keep `'unsafe-inline'`: React style attributes, Recharts and the toast library set
  inline styles. No script is allowed inline or from another host.
- `vite preview` reads the same file, so the built app can be checked against the policy locally.

## Consequences
- A new third-party script, font or API host needs a change here first.
- Inline `<script>` in `index.html` is banned; put it in `public/`.
