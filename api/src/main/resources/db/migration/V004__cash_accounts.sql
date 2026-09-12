-- The first syncable record type (FR-AST-08). Balance in whole minor units (BR-01).

create table cash_account (
    id           uuid        primary key,
    household_id uuid        not null references household (id) on delete cascade,
    owner_id     uuid        not null references app_user (id),
    visibility   varchar(8)  not null default 'SHARED',
    name         varchar(80) not null,
    kind         varchar(16) not null,
    institution  varchar(80),
    balance      bigint      not null default 0,
    currency     varchar(3)  not null,
    sort_order   integer     not null default 0,
    field_clocks jsonb       not null default '{}'::jsonb,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz,
    constraint ck_cash_account_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_cash_account_household on cash_account (household_id) where deleted_at is null;

call make_syncable('cash_account');
