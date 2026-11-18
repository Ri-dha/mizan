# T-storage-public-endpoint — Presign storage URLs a browser can reach
- Status: proposed (2026-11-18)
- Context: found while writing ADR csp-and-job-lock-2.
- Notes: `S3Config` presigns with `MIZAN_STORAGE_ENDPOINT`, which the prod Compose file sets to
  `http://seaweedfs:8333` (before ADR storage-1, `http://minio:9000`), an address only containers can reach, so receipt uploads and downloads
  would fail in a deployment. Either presign with a separate public endpoint
  (`MIZAN_STORAGE_PUBLIC_ENDPOINT`) or proxy storage through Caddy on the same origin (then the
  CSP needs no storage origin). Decide, then check a receipt round trip on the prod profile. SeaweedFS has `-s3.externalUrl` for
  verifying signatures made for a public host behind a proxy.
