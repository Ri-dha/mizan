# 2026-11-18 — Receipt OCR assets
tesseract.js chooses its core by browser support: plain, SIMD, or relaxed SIMD. Current Chrome
supports relaxed SIMD and asks for `tesseract-core-relaxedsimd-lstm.wasm.js`. If that file is
missing from `/ocr`, the static server answers with `index.html` (status 200), the worker fails
with "MIME type ('text/html') is not executable", and the service worker's `CacheFirst` kept that
HTML, so OCR stayed broken until site data was cleared. `scripts/fetch-ocr-assets.mjs` now copies
all three cores, and `src/sw.ts` caches no HTML under `/ocr/`. After changing OCR files, test in a
fresh profile or clear the `ocr-assets` cache.
Source: T-csp-and-job-lock
