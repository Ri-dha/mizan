-- Notifications (FR-NTF-01..06): per-member settings that sync, Web Push subscriptions per
-- device, and a log that keeps every reminder to once per day and subject.

alter table recurring_expense add column reminder_days integer;

create table notification_setting (
    id                          uuid         primary key,
    household_id                uuid         not null references household (id) on delete cascade,
    owner_id                    uuid         not null references app_user (id),
    visibility                  varchar(8)   not null default 'PRIVATE',
    bill_due                    boolean      not null default true,
    bill_lead_days              integer      not null default 3,
    pay_day                     boolean      not null default true,
    overspend                   boolean      not null default true,
    overspend_threshold_bp      integer      not null default 9000,
    month_close                 boolean      not null default true,
    metal_price                 boolean      not null default false,
    metal_move_bp               integer      not null default 200,
    quiet_mode                  boolean      not null default false,
    field_clocks                jsonb        not null default '{}'::jsonb,
    created_at                  timestamptz  not null default now(),
    updated_at                  timestamptz  not null default now(),
    deleted_at                  timestamptz,
    constraint ck_notification_setting_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_notification_setting_lead check (bill_lead_days between 0 and 30),
    constraint ck_notification_setting_threshold check (overspend_threshold_bp between 1 and 20000)
);

call make_syncable('notification_setting');

create table push_subscription (
    id            uuid          primary key,
    user_id       uuid          not null references app_user (id) on delete cascade,
    household_id  uuid          not null references household (id) on delete cascade,
    endpoint      varchar(1024) not null,
    p256dh        varchar(128)  not null,
    auth          varchar(64)   not null,
    user_agent    varchar(200),
    created_at    timestamptz   not null default now(),
    failures      integer       not null default 0
);

create unique index ux_push_subscription_endpoint on push_subscription (endpoint);
create index ix_push_subscription_user on push_subscription (user_id);

create table notification_log (
    id          uuid         primary key,
    user_id     uuid         not null references app_user (id) on delete cascade,
    kind        varchar(24)  not null,
    dedupe_key  varchar(160) not null,
    sent_at     timestamptz  not null default now()
);

create unique index ux_notification_log_once on notification_log (user_id, kind, dedupe_key);
