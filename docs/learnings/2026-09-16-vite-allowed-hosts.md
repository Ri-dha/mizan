# 2026-09-16 — Vite rejects unknown Host headers
Tunnels present their own hostname and Vite answers 403. ngrok domains are allowed in
`vite.config.ts`; any other tunnel needs `VITE_ALLOWED_HOST`.
Source: T-install-biometrics
