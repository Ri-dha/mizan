# 2026-11-18 — Testing two accounts in one browser
Each account's device state lives in IndexedDB per origin, so two tabs on `localhost:5173` share
one device. Open the second account on `http://member.localhost:5173`: Chrome resolves
`*.localhost` to loopback, gives it its own storage, and the Vite proxy still reaches the API
(`127.0.0.1` is refused by the preview browser). The app locks again whenever Vite reloads after
a source edit; that is expected, not a lock bug.
Source: T-e2e-encryption
