# T-styles — Accept the template choice on the Home layout row
- Status: in-progress (2026-11-22)
- Context: ADR styles-1 in MizanApp; same shape as T-app-mode.
- Notes: `appStyle` is one more sealed text field in `HomeLayoutSyncTable`; no migration, the server
  enforces nothing. Covered by `HouseholdSharingIntegrationTest`.
