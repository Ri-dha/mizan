-- Debts in both directions with a payment ledger (FR-DBT-01..06, BR-06) and savings goals
-- with a deposit ledger (FR-GOL-01..05). Balances and progress derive from the ledgers.

create table debt (
    id                       uuid         primary key,
    household_id             uuid         not null references household (id) on delete cascade,
    owner_id                 uuid         not null references app_user (id),
    visibility               varchar(8)   not null default 'SHARED',
    name                     varchar(80)  not null,
    counterparty             varchar(80),
    direction                varchar(6)   not null,
    principal                bigint       not null,
    currency                 varchar(3)   not null,
    fx_rate_micros           bigint       not null default 1000000,
    annual_rate_basis_points integer      not null default 0,
    term_months              integer,
    monthly_payment          bigint       not null default 0,
    bucket_id                uuid,
    start_date               date         not null,
    status                   varchar(10)  not null default 'ACTIVE',
    note                     text,
    field_clocks             jsonb        not null default '{}'::jsonb,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    deleted_at               timestamptz,
    constraint ck_debt_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_debt_direction check (direction in ('OWING', 'OWED')),
    constraint ck_debt_status check (status in ('ACTIVE', 'SETTLED'))
);

create index ix_debt_household on debt (household_id) where deleted_at is null;
call make_syncable('debt');

create table debt_payment (
    id                   uuid         primary key,
    household_id         uuid         not null references household (id) on delete cascade,
    owner_id             uuid         not null references app_user (id),
    visibility           varchar(8)   not null default 'SHARED',
    debt_id              uuid         not null references debt (id) deferrable initially deferred,
    paid_on              date         not null,
    month_key            varchar(7)   not null,
    amount               bigint       not null,
    interest_component   bigint       not null default 0,
    principal_component  bigint       not null,
    transaction_id       uuid,
    note                 text,
    field_clocks         jsonb        not null default '{}'::jsonb,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    deleted_at           timestamptz,
    constraint ck_debt_payment_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_debt_payment_debt on debt_payment (debt_id) where deleted_at is null;
call make_syncable('debt_payment');

create table goal (
    id                    uuid         primary key,
    household_id          uuid         not null references household (id) on delete cascade,
    owner_id              uuid         not null references app_user (id),
    visibility            varchar(8)   not null default 'SHARED',
    name                  varchar(80)  not null,
    target_amount         bigint       not null,
    currency              varchar(3)   not null,
    target_date           date,
    monthly_contribution  bigint,
    bucket_id             uuid,
    backing_asset_type    varchar(16),
    backing_asset_id      uuid,
    status                varchar(10)  not null default 'ACTIVE',
    completed_on          date,
    note                  text,
    sort_order            integer      not null default 0,
    field_clocks          jsonb        not null default '{}'::jsonb,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    deleted_at            timestamptz,
    constraint ck_goal_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_goal_status check (status in ('ACTIVE', 'COMPLETED', 'ARCHIVED'))
);

create index ix_goal_household on goal (household_id) where deleted_at is null;
call make_syncable('goal');

create table goal_deposit (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    goal_id         uuid         not null references goal (id) deferrable initially deferred,
    deposited_on    date         not null,
    month_key       varchar(7)   not null,
    amount          bigint       not null,
    direction       varchar(10)  not null,
    transaction_id  uuid,
    note            text,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_goal_deposit_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_goal_deposit_direction check (direction in ('DEPOSIT', 'WITHDRAWAL'))
);

create index ix_goal_deposit_goal on goal_deposit (goal_id) where deleted_at is null;
call make_syncable('goal_deposit');
