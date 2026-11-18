# 2026-09-13 — Column too short means a new migration
When a value outgrows a column (`account_status` for DELETION_REQUESTED, `user_agent` for long
mobile User-Agents), add the next `V0NN__widen_...` migration and, where the input is external,
truncate at capture too. Never edit an applied migration; Flyway checksums will fail.
Source: T-m5-m7, T-install-biometrics
