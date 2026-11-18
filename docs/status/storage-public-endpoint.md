# Status — T-storage-public-endpoint

- Server half: `b48b895` on develop and main (branch `feat/storage-public-endpoint`).
- Works: links signed for `MIZAN_STORAGE_PUBLIC_ENDPOINT` (internal endpoint when blank), SeaweedFS
  given `S3_EXTERNAL_URL`, its port bound to 127.0.0.1.
- Validated: API 95 tests. `PublicStorageLinkTest` runs SeaweedFS with signature checks behind a
  real Caddy (`caddy reverse-proxy`): a link for the public address uploads and downloads, a
  tampered one gets 403. The same test passes without `S3_EXTERNAL_URL`, because Caddy keeps `Host`.
- Web half done: MizanApp f53b409 and 1e973a6 here; checked end to end with the production web image.
