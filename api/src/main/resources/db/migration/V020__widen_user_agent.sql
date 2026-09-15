-- Some mobile and in-app browsers send User-Agent strings well past 256 characters, which made
-- registration and sign-in fail on the refresh token insert. The API also truncates, so the
-- column is a ceiling rather than a guard.
alter table refresh_token alter column user_agent type varchar(1024);
