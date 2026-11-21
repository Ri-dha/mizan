# T-followups-2026-11-21 — Debt accounts, report image retention, instant sync (server)
- Status: done (d475adf)
- Context: user request 2026-11-21 (plan "five follow-ups"). Clients in Ri-dha/MizanApp.
- Notes:
  - T-debt-accounts: `DebtSyncTable` accepts sealed `cashAccountId` (where a loan's money came from or went into).
  - T-report-image-retention (ADR support-2): V031 `support_attachment.removed_at`; `ReportImageExpiry` deletes photos of reports
    resolved more than `mizan.support.image-retention` (180 days) ago; `ReportAttachment.removedAt`; removed images 410.
  - T-instant-sync: ADR sync-4, `GET /api/v1/sync/wait`, `SyncSignal`, `mizan.sync.wait-timeout`.
