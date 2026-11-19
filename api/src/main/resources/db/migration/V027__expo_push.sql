-- Phones receive reminders through Expo's push service (ADR push-1): a subscription is either a
-- browser endpoint with its encryption keys, or an Expo push token, which needs neither key.
alter table push_subscription add column channel varchar(8) not null default 'WEB_PUSH';
alter table push_subscription alter column p256dh drop not null;
alter table push_subscription alter column auth drop not null;
alter table push_subscription add constraint ck_push_subscription_channel check (
    (channel = 'WEB_PUSH' and p256dh is not null and auth is not null)
    or (channel = 'EXPO' and p256dh is null and auth is null));
