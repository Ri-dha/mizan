-- ADR sync-5: every edit rewrites a whole sealed record, so these two tables collect dead rows
-- faster than the default 20% trigger suits once they are large. Free space on each page lets an
-- edit stay on its page and skip the indexes, since it never changes id, household or type.
alter table vault_record set (autovacuum_vacuum_scale_factor = 0.02, autovacuum_vacuum_threshold = 1000, fillfactor = 85);
alter table sync_log set (autovacuum_vacuum_scale_factor = 0.02, autovacuum_vacuum_threshold = 1000);
