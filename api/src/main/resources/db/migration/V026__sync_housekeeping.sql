-- ADR sync-2: the nightly housekeeping finds superseded log entries by record and old receipts by age.
create index ix_sync_log_row on sync_log (table_name, row_id, seq);
create index ix_sync_op_receipt_applied on sync_op_receipt (applied_at);
