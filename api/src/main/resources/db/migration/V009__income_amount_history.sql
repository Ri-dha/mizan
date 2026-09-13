-- A source's amount over time (a raise, a cut), so earlier months keep planning on what was
-- true then (BR-09). income_source.amount stays the latest known amount.

create table income_source_amount (
    id                uuid         primary key,
    household_id      uuid         not null references household (id) on delete cascade,
    owner_id          uuid         not null references app_user (id),
    visibility        varchar(8)   not null default 'SHARED',
    income_source_id  uuid         not null references income_source (id) deferrable initially deferred,
    effective_from    date         not null,
    amount            bigint       not null,
    currency          varchar(3)   not null,
    fx_rate_micros    bigint       not null default 1000000,
    note              text,
    field_clocks      jsonb        not null default '{}'::jsonb,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    deleted_at        timestamptz,
    constraint ck_income_source_amount_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_income_source_amount_source on income_source_amount (income_source_id, effective_from) where deleted_at is null;
call make_syncable('income_source_amount');
