# T-sync-rejected-ops — One refused op must not stop a device's sync
- Status: done (2026-11-20): MizanApi bebc6d5, MizanApp 500d790, on develop and main
- Context: found while taking the store screenshots: a demo loader wrote `fxRateMicros` to cash
  accounts and goals, the server refused the whole batch, and the phone retried it forever ("42
  changes waiting"). ADR sync-3.
- Notes: `SyncService.push` reports op-level refusals in `SyncPushResponse.rejected`
  (`RejectedOpResponse`) and applies the rest; `VaultRowRepository.insert` uses `on conflict (id) do
  nothing` so a taken id is refused without aborting the transaction. Client: `sync/engine.ts` drops
  rejected ops from the outbox and records them in meta `sync.rejections`
  (`db/syncRejections.ts`, table, row, code and time only); web and phone Sync screens list them
  with Dismiss; table names in en and ar under `sync.tables`. Tests: API 120 (mixed batch with an
  unknown field and a foreign id; refusal tests now read `rejected`), core 196, web 35. Checked on
  the iPhone simulator against the dev API: three refused cash accounts listed, the rest synced,
  "Up to date".
