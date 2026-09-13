-- Cash balance adjustments (FR-AST-08), month-end snapshots (FR-NET-03, BR-09) and month
-- close state (BR-14). Snapshots are written by the server only and are immutable; clients
-- pull them like any other row but may not push.

create table cash_adjustment (
    id                uuid         primary key,
    household_id      uuid         not null references household (id) on delete cascade,
    owner_id          uuid         not null references app_user (id),
    visibility        varchar(8)   not null default 'SHARED',
    cash_account_id   uuid         not null references cash_account (id) deferrable initially deferred,
    adjusted_on       date         not null,
    previous_balance  bigint       not null,
    new_balance       bigint       not null,
    note              text,
    field_clocks      jsonb        not null default '{}'::jsonb,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    deleted_at        timestamptz,
    constraint ck_cash_adjustment_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_cash_adjustment_account on cash_adjustment (cash_account_id) where deleted_at is null;
call make_syncable('cash_adjustment');

create table net_worth_snapshot (
    id                 uuid         primary key,
    household_id       uuid         not null references household (id) on delete cascade,
    owner_id           uuid         not null references app_user (id),
    visibility         varchar(8)   not null default 'SHARED',
    month_key          varchar(7)   not null,
    taken_at           timestamptz  not null,
    total_assets       bigint       not null,
    total_liabilities  bigint       not null,
    net_worth          bigint       not null,
    composition        jsonb        not null,
    rate_set           jsonb        not null,
    field_clocks       jsonb        not null default '{}'::jsonb,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    deleted_at         timestamptz,
    constraint ck_net_worth_snapshot_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_net_worth_snapshot_month on net_worth_snapshot (household_id, month_key, taken_at desc);
call make_syncable('net_worth_snapshot');

-- A snapshot never changes after it is taken; the only permitted write is the sync stamp
-- bookkeeping the engine does at insert time.
create function net_worth_snapshot_immutable() returns trigger language plpgsql as $$
begin
    raise exception 'net_worth_snapshot is immutable';
end $$;

create trigger trg_net_worth_snapshot_immutable
    before update or delete on net_worth_snapshot
    for each row execute function net_worth_snapshot_immutable();

-- One row per household month; closed_at set on close, reopened_at set on reopen, and a
-- second close writes a fresh snapshot and clears reopened_at.
create table month_close (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    owner_id      uuid         not null references app_user (id),
    visibility    varchar(8)   not null default 'SHARED',
    month_key     varchar(7)   not null,
    snapshot_id   uuid         references net_worth_snapshot (id),
    closed_at     timestamptz,
    reopened_at   timestamptz,
    field_clocks  jsonb        not null default '{}'::jsonb,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz,
    constraint ck_month_close_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create unique index ux_month_close_household_month on month_close (household_id, month_key);
call make_syncable('month_close');
