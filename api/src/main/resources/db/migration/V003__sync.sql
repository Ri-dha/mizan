-- Change feed, device cursors, idempotency receipts and conflict log (FR-TRX-07, NFR-03).

create table sync_device (
    id            uuid        primary key,
    household_id  uuid        not null references household (id) on delete cascade,
    user_id       uuid        not null references app_user (id) on delete cascade,
    name          varchar(80) not null,
    last_pull_seq bigint      not null default 0,
    last_seen_at  timestamptz not null default now(),
    created_at    timestamptz not null default now()
);

create index ix_sync_device_household on sync_device (household_id);

create table sync_log (
    seq          bigserial   primary key,
    household_id uuid        not null,
    table_name   varchar(64) not null,
    row_id       uuid        not null,
    changed_at   timestamptz not null default now()
);

create index ix_sync_log_household_seq on sync_log (household_id, seq);

create table sync_op_receipt (
    op_id      uuid        primary key,
    device_id  uuid        not null references sync_device (id) on delete cascade,
    applied_at timestamptz not null default now()
);

create table sync_conflict (
    id           uuid        primary key,
    household_id uuid        not null,
    device_id    uuid        not null,
    table_name   varchar(64) not null,
    row_id       uuid        not null,
    field        varchar(64) not null,
    client_value jsonb,
    server_value jsonb,
    client_clock varchar(64) not null,
    server_clock varchar(64) not null,
    detected_at  timestamptz not null default now()
);

create index ix_sync_conflict_household on sync_conflict (household_id, detected_at desc);

-- Every write to a syncable table lands in the feed, whoever made it — a scheduled job or an
-- import reaches clients exactly like a push does.
create function sync_log_row() returns trigger language plpgsql as $$
begin
    insert into sync_log (household_id, table_name, row_id)
    values (new.household_id, tg_table_name, new.id);
    return new;
end $$;

create procedure make_syncable(tbl regclass)
language plpgsql as $$
begin
    execute format($t$
        create trigger trg_%s_sync_log
            after insert or update on %s
            for each row execute function sync_log_row()
    $t$, tbl, tbl);
    call apply_household_rls(tbl, true);
end $$;

call apply_household_rls('sync_device', false);
call apply_household_rls('sync_log', false);
call apply_household_rls('sync_conflict', false);
