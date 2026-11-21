# T-synced-preferences — Reading preferences follow the person across devices
- Status: done (2026-11-21, on develop and main)
- Context: user request 2026-11-21; ADR home-2; the clients are in Ri-dha/MizanApp.
- Notes: `HomeLayoutSyncTable` accepts four more sealed fields; no migration (vault storage).
  `HouseholdSharingIntegrationTest` pushes them and pulls them on a second device. Ship before
  the clients: an older server rejects the fields with `SYNC_FIELD_UNKNOWN`.
