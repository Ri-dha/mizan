# Handover — 2026-11-19 — Storage links a browser can reach (server half)

## Completed (uncommitted, MizanApi, branch feat/storage-public-endpoint)
- ADR storage-4; `StorageProperties.publicEndpoint` and `linkEndpoint()`; `S3Config` presigner
  uses it; `application.yml` `public-endpoint`; compose: API env, SeaweedFS `S3_EXTERNAL_URL`,
  port on 127.0.0.1; `.env.example`; RUNBOOK deploy step and storage section; ARCHITECTURE.
- `PublicStorageLinkTest` (needs the `caddy:2-alpine` and `chrislusf/seaweedfs:4.51` images).

## In flight
- T-storage-public-endpoint client half in MizanApp (see the task file). Not started; another
  session is working in MizanApp on the phone app.

## Next steps
- Owner's word to commit and merge into develop; then the MizanApp half.

## Blockers
- None.

## Gotchas hit this session
- S3 signatures cover host and path: Caddy must not strip a prefix or rewrite `Host`.
