# 2026-09-16 — Phones need HTTPS even on the LAN
WebCrypto, WebAuthn and service workers exist only on a secure origin, so `http://<lan-ip>`
loads but the PIN screen fails. Use `npm run dev:lan` (self-signed HTTPS) or ngrok. iOS Safari
will not register a service worker behind a self-signed certificate; use mkcert and trust its
root on the phone. Through Vite's proxy the API sees the phone's Origin, so the proxy rewrites
it to the dev origin; without that the API answers 403.
Source: T-install-biometrics
