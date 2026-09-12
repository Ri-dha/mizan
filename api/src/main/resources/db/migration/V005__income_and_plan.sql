-- Income sources and receipts (FR-INC-01..04), plan versions and buckets (FR-PLN-01..06).
-- Amounts are whole minor units (BR-01); FX rates are stored ×1,000,000 so history never
-- depends on a rate that moved later (BR-09).

create table income_source (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    name            varchar(80)  not null,
    amount          bigint       not null,
    currency        varchar(3)   not null,
    fx_rate_micros  bigint       not null default 1000000,
    frequency       varchar(12)  not null,
    pay_day         integer,
    anchor_date     date,
    active_from     date         not null,
    active_to       date,
    note            text,
    sort_order      integer      not null default 0,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_income_source_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_income_source_frequency check (frequency in ('MONTHLY', 'BIWEEKLY', 'WEEKLY', 'ONE_OFF')),
    constraint ck_income_source_pay_day check (pay_day is null or pay_day between 1 and 31)
);

create index ix_income_source_household on income_source (household_id) where deleted_at is null;
call make_syncable('income_source');

create table income_receipt (
    id                uuid         primary key,
    household_id      uuid         not null references household (id) on delete cascade,
    owner_id          uuid         not null references app_user (id),
    visibility        varchar(8)   not null default 'SHARED',
    income_source_id  uuid         references income_source (id) deferrable initially deferred,
    month_key         varchar(7)   not null,
    received_on       date         not null,
    amount            bigint       not null,
    currency          varchar(3)   not null,
    fx_rate_micros    bigint       not null default 1000000,
    base_amount       bigint       not null,
    note              text,
    field_clocks      jsonb        not null default '{}'::jsonb,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    deleted_at        timestamptz,
    constraint ck_income_receipt_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_income_receipt_month on income_receipt (household_id, month_key) where deleted_at is null;
call make_syncable('income_receipt');

-- A plan version applies from effective_from until effective_to (both month keys, inclusive).
-- Editing a plan for a later month creates a new version, so a month keeps the percentages
-- it ran under (FR-PLN-06).
create table plan (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    effective_from  varchar(7)   not null,
    effective_to    varchar(7),
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_plan_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_plan_household on plan (household_id, effective_from) where deleted_at is null;
call make_syncable('plan');

-- share_basis_points: hundredths of a percent, so one decimal place of percentage is exact.
create table bucket (
    id                  uuid         primary key,
    household_id        uuid         not null references household (id) on delete cascade,
    owner_id            uuid         not null references app_user (id),
    visibility          varchar(8)   not null default 'SHARED',
    plan_id             uuid         not null references plan (id) deferrable initially deferred,
    name                varchar(80)  not null,
    colour              varchar(16)  not null default '#88aaee',
    share_basis_points  integer      not null default 0,
    fixed_amount        bigint,
    carry_over          boolean      not null default false,
    sort_order          integer      not null default 0,
    field_clocks        jsonb        not null default '{}'::jsonb,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    deleted_at          timestamptz,
    constraint ck_bucket_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_bucket_share check (share_basis_points between 0 and 10000)
);

create index ix_bucket_plan on bucket (plan_id) where deleted_at is null;
call make_syncable('bucket');
