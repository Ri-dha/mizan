-- Moving money between buckets with a reason (FR-PLN-03). The money itself moves as a TRANSFER
-- transaction with a BUCKET counterparty, so the month maths already applies; this row keeps the
-- reason and makes the month's audit trail a single query.

create table bucket_move (
    id              uuid         primary key,
    household_id    uuid         not null references household (id) on delete cascade,
    owner_id        uuid         not null references app_user (id),
    visibility      varchar(8)   not null default 'SHARED',
    from_bucket_id  uuid         not null references bucket (id) deferrable initially deferred,
    to_bucket_id    uuid         not null references bucket (id) deferrable initially deferred,
    month_key       varchar(7)   not null,
    moved_on        date         not null,
    amount          bigint       not null,
    reason          text,
    transaction_id  uuid         references ledger_transaction (id) deferrable initially deferred,
    field_clocks    jsonb        not null default '{}'::jsonb,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    deleted_at      timestamptz,
    constraint ck_bucket_move_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_bucket_move_amount check (amount > 0),
    constraint ck_bucket_move_distinct check (from_bucket_id <> to_bucket_id)
);

create index ix_bucket_move_month on bucket_move (household_id, month_key) where deleted_at is null;
call make_syncable('bucket_move');
