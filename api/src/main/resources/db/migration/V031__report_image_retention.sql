-- T-report-image-retention: problem-report photos are deleted some months after the report is
-- resolved. The row stays, marked, so the thread can say a photo was there and was removed.
alter table support_attachment add column removed_at timestamptz;
