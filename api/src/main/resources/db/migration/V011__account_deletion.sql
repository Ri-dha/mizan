-- FR-ACC-08: a deletion request starts a grace window; the purge job removes the account and
-- its household once the window has passed.
alter table app_user add column deletion_requested_at timestamptz;
