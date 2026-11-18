# T-storage-public-endpoint — Presign storage URLs a browser can reach
- Status: in-review (2026-11-19), server half b48b895; web half on feat/storage-same-origin in
  MizanApp plus the web service default here (MIZAN_STORAGE_ORIGIN empty), uncommitted
- Context: found while writing ADR csp-and-job-lock-2; ADR storage-4. Owner chose the site's own
  address over a storage subdomain, and asked for the server change first (2026-11-19).
- Notes: `S3Config` presigns with `MIZAN_STORAGE_ENDPOINT`, which the prod Compose file sets to
  `http://seaweedfs:8333` (before ADR storage-1, `http://minio:9000`), an address only containers can reach, so receipt uploads and downloads
  would fail in a deployment. Either presign with a separate public endpoint
  (`MIZAN_STORAGE_PUBLIC_ENDPOINT`) or proxy storage through Caddy on the same origin (then the
  CSP needs no storage origin). Decide, then check a receipt round trip on the prod profile. SeaweedFS has `-s3.externalUrl` for
  verifying signatures made for a public host behind a proxy.
- Server half (MizanApi), done: `public-endpoint` signs links, `S3_EXTERNAL_URL` on SeaweedFS,
  port 8333 on 127.0.0.1, `PublicStorageLinkTest` (SeaweedFS checking signatures behind a real
  Caddy proxy: upload, download, a tampered link refused).
- Client half (MizanApp), open: Caddy route `handle /mizan-attachments/* { reverse_proxy
  seaweedfs:8333 }` before the SPA fallback; drop the storage origin from
  `security-headers.caddy`, the vite preview headers and `securityHeaders.test.ts`; then the
  `MIZAN_STORAGE_ORIGIN` env on the web service here can go. Check a receipt on the prod profile.
