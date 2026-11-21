# T-app-mode — Accept the simple or advanced choice on the Home layout row (server)
- Status: in review
- Context: user request 2026-11-22; ADR mode-1 in Ri-dha/MizanApp. The mode is a display choice; the server
  stores it sealed with the other reading preferences (ADR home-2) and enforces nothing.
- Notes: `HomeLayoutSyncTable` accepts `appMode`; covered by `HouseholdSharingIntegrationTest`.
