# T-drop-plain-tables — Drop the plain record tables and tune the record tables for growth
- Status: done (2026-11-23)
- Context: ADR sync-5; ADR e2e-1; ADR sync-2.
- Notes: V032 drops the 27 empty plain tables and the code that read them; V033 sets autovacuum and
  fillfactor on `vault_record` and `sync_log`; sync housekeeping removes log entries of purged
  records. Checked with `./mvnw test` (all green).
