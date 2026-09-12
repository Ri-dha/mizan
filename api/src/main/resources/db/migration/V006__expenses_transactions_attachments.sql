-- Recurring bills and their settlements (FR-EXP-01..05), the transaction ledger (FR-TRX-01..05),
-- receipt attachments (FR-TRX-02), and a system bypass for household policies so server jobs
-- such as the recycle-bin purge (BR-15) can see every household.

create or replace procedure apply_household_rls(tbl regclass, with_visibility boolean)
language plpgsql as $$
begin
    execute format('alter table %s enable row level security', tbl);
    execute format('alter table %s force row level security', tbl);
    execute format('drop policy if exists household_scope on %s', tbl);
    if with_visibility then
        execute format($p$
            create policy household_scope on %s
                using (current_setting('app.system', true) = 'on'
                       or (household_id = app_household_id()
                           and (visibility = 'SHARED' or owner_id = app_user_id())))
                with check (current_setting('app.system', true) = 'on'
                            or household_id = app_household_id())
        $p$, tbl);
    else
        execute format($p$
            create policy household_scope on %s
                using (current_setting('app.system', true) = 'on' or household_id = app_household_id())
                with check (current_setting('app.system', true) = 'on' or household_id = app_household_id())
        $p$, tbl);
    end if;
end $$;

do $$
declare t text;
begin
    foreach t in array array['cash_account', 'income_source', 'income_receipt', 'plan', 'bucket'] loop
        call apply_household_rls(t::regclass, true);
    end loop;
    foreach t in array array['sync_device', 'sync_log', 'sync_conflict'] loop
        call apply_household_rls(t::regclass, false);
    end loop;
end $$;

create table recurring_expense (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    name            varchar(80)  not null,
    bucket_id       uuid         references bucket (id) deferrable initially deferred,
    amount          bigint       not null,
    is_estimate     boolean      not null default false,
    currency        varchar(3)   not null,
    fx_rate_micros  bigint       not null default 1000000,
    frequency       varchar(12)  not null,
    due_day         integer,
    anchor_date     date,
    interval_days   integer,
    active_from     date         not null,
    active_to       date,
    category        varchar(40),
    note            text,
    sort_order      integer      not null default 0,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_recurring_expense_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_recurring_expense_frequency check (frequency in ('MONTHLY', 'QUARTERLY', 'ANNUAL', 'CUSTOM'))
);

create index ix_recurring_expense_household on recurring_expense (household_id) where deleted_at is null;
call make_syncable('recurring_expense');

-- One row per settled due date; an unpaid due date is derived from the schedule and has no row.
create table expense_occurrence (
    id                    uuid         primary key,
    household_id          uuid         not null references household (id) on delete cascade,
    owner_id              uuid         not null references app_user (id),
    visibility            varchar(8)   not null default 'SHARED',
    recurring_expense_id  uuid         not null references recurring_expense (id) deferrable initially deferred,
    due_date              date         not null,
    month_key             varchar(7)   not null,
    expected_amount       bigint       not null,
    actual_amount         bigint       not null,
    paid_on               date         not null,
    transaction_id        uuid,
    field_clocks          jsonb        not null default '{}'::jsonb,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    deleted_at            timestamptz,
    constraint ck_expense_occurrence_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_expense_occurrence_month on expense_occurrence (household_id, month_key) where deleted_at is null;
call make_syncable('expense_occurrence');

-- "transaction" is a keyword in too many tools to be a comfortable table name.
create table ledger_transaction (
    id                 uuid         primary key,
    household_id       uuid         not null references household (id) on delete cascade,
    owner_id           uuid         not null references app_user (id),
    visibility         varchar(8)   not null default 'SHARED',
    type               varchar(10)  not null,
    occurred_on        date         not null,
    month_key          varchar(7)   not null,
    amount             bigint       not null,
    currency           varchar(3)   not null,
    fx_rate_micros     bigint       not null default 1000000,
    base_amount        bigint       not null,
    bucket_id          uuid,
    category           varchar(40),
    payee              varchar(80),
    note               text,
    counterparty_type  varchar(16),
    counterparty_id    uuid,
    attachment_id      uuid,
    field_clocks       jsonb        not null default '{}'::jsonb,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    deleted_at         timestamptz,
    constraint ck_ledger_transaction_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_ledger_transaction_type check (type in ('EXPENSE', 'INCOME', 'TRANSFER')),
    constraint ck_ledger_transaction_counterparty check (counterparty_type is null
        or counterparty_type in ('BUCKET', 'GOAL', 'DEBT', 'CASH_ACCOUNT'))
);

create index ix_ledger_transaction_month on ledger_transaction (household_id, month_key) where deleted_at is null;
call make_syncable('ledger_transaction');

-- Bytes live in object storage under <household>/<attachment id>, encrypted on the device
-- before upload; the server never holds a key.
create table attachment (
    id             uuid         primary key,
    household_id   uuid         not null references household (id) on delete cascade,
    owner_id       uuid         not null references app_user (id),
    visibility     varchar(8)   not null default 'SHARED',
    owner_type     varchar(24)  not null,
    owner_record_id uuid        not null,
    mime_type      varchar(80)  not null,
    byte_size      bigint       not null,
    iv             varchar(64)  not null,
    uploaded_at    timestamptz,
    field_clocks   jsonb        not null default '{}'::jsonb,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    deleted_at     timestamptz,
    constraint ck_attachment_visibility check (visibility in ('SHARED', 'PRIVATE'))
);

create index ix_attachment_owner on attachment (household_id, owner_record_id) where deleted_at is null;
call make_syncable('attachment');
