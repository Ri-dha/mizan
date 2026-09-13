-- DELETION_REQUESTED did not fit the original column.
alter table app_user alter column account_status type varchar(24);
