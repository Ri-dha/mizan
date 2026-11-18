-- T-admin-dashboard (ADR admin-1): operators run the service; they can never read records (ADR e2e-1).

alter table app_user
    add column platform_role varchar(16) not null default 'NONE',
    add column operator_elevated_until timestamptz,
    add column suspended_at timestamptz,
    add constraint ck_app_user_platform_role check (platform_role in ('NONE', 'OPERATOR'));

-- A database value overrides the environment default. Secrets are AES-GCM ciphertext under
-- MIZAN_SETTINGS_KEY, which never leaves the environment; plain values stay in text_value.
create table platform_setting (
    setting_key   varchar(80)  primary key,
    text_value    text,
    secret_value  bytea,
    updated_by    uuid         not null references app_user (id),
    updated_at    timestamptz  not null default now(),
    constraint ck_platform_setting_one_value check ((text_value is null) <> (secret_value is null))
);

create table announcement (
    id            uuid         primary key,
    message_en    varchar(500) not null,
    message_ar    varchar(500) not null,
    level         varchar(8)   not null default 'INFO',
    starts_at     timestamptz  not null default now(),
    ends_at       timestamptz,
    created_by    uuid         not null references app_user (id),
    created_at    timestamptz  not null default now(),
    retracted_at  timestamptz,
    constraint ck_announcement_level check (level in ('INFO', 'WARNING'))
);

create index ix_announcement_live on announcement (starts_at) where retracted_at is null;

grant select, insert, update, delete on platform_setting, announcement to mizan_app;
