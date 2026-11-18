# ADR storage-4 — Storage links are signed for the public address

- Date: 2026-11-19
- Status: accepted
- Task: T-storage-public-endpoint

## Context
Browsers and phones upload and fetch receipts and backups straight from object storage with
links the API signs. The API signed them for `MIZAN_STORAGE_ENDPOINT`, which in a deployment is
`http://seaweedfs:8333`, a name only containers resolve, so every upload and download would fail.
Options: give storage its own public host (`files.<site>`, a second certificate and a second CSP
origin) or serve it under the site through Caddy. The owner chose the site's own address. An S3
signature covers the host and path, so the proxy may not change either.

## Decision
- `mizan.storage.public-endpoint` (`MIZAN_STORAGE_PUBLIC_ENDPOINT`) is the address in every
  signed link; blank, links use the internal endpoint, as in development. The S3 client keeps
  the internal endpoint for the API's own calls (bucket creation, expiry, purge).
- Links stay path-style (`<site>/mizan-attachments/<key>`), so Caddy forwards
  `/mizan-attachments/*` to storage without rewriting the path, and keeps `Host` as received.
  That route and the CSP change belong to the web repo (Ri-dha/MizanApp, `infra/caddy`).
- SeaweedFS is given the same address as `S3_EXTERNAL_URL`. With Caddy's default it is not
  needed (a test passes without it); it keeps signatures valid behind a proxy that rewrites `Host`.
- SeaweedFS's port is bound to 127.0.0.1, so a deployment reaches it only through Caddy.

## Consequences
- Receipts work in a deployment once the Caddy route exists; no extra host or certificate.
- A proxy that strips a path prefix breaks every signature; storage must sit at the site root
  under the bucket's own path.
- Phones on the LAN in development cannot reach `localhost:8333`; set the public endpoint to the
  Mac's address for device testing.
