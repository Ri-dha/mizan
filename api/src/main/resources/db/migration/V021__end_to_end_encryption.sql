-- End-to-end encryption (ADR e2e-1). The server stops holding readable financial data: records
-- move to vault_record as per-field ciphertext, and the key directory holds only wrapped keys.
-- The typed tables stay readable for sync pull until each household's devices have migrated
-- them, then their rows are deleted.

-- Passwords never reach the server again: it receives a client-derived auth key. LEGACY rows
-- still hold a hash of the password until their next sign-in upgrades them.
alter table app_user add column auth_scheme varchar(8) not null default 'LEGACY';
alter table app_user add constraint ck_app_user_auth_scheme check (auth_scheme in ('LEGACY', 'KEY'));

-- One per user: the user key wrapped by the password-derived key and by the recovery key, the
-- ECDH public key others wrap household keys to, and the private key encrypted by the user key.
create table user_key (
    user_id                uuid         primary key references app_user (id) on delete cascade,
    wrapped_by_password    text         not null,
    wrapped_by_recovery    text         not null,
    public_key             text         not null,
    encrypted_private_key  text         not null,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now()
);

-- A household or report key wrapped to one member's public key.
create table household_key_grant (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    user_id       uuid         not null references app_user (id) on delete cascade,
    key_kind      varchar(10)  not null,
    key_version   integer      not null,
    wrapped_key   text         not null,
    granted_by    uuid         references app_user (id) on delete set null,
    created_at    timestamptz  not null default now(),
    constraint ck_household_key_grant_kind check (key_kind in ('HOUSEHOLD', 'REPORT')),
    constraint ck_household_key_grant_version check (key_version >= 1)
);

create unique index ux_household_key_grant on household_key_grant (household_id, user_id, key_kind, key_version);
call apply_household_rls('household_key_grant', false);

alter table household add column vault_migrated_at timestamptz;

-- Every synced record from now on. Only visibility and deleted_at are plain: row-level security
-- and the recycle-bin purge need them. Field values are 'e1.<h|u|r>.<version>.<base64url>'.
create table vault_record (
    id            uuid         primary key,
    household_id  uuid         not null references household (id) on delete cascade,
    owner_id      uuid         not null references app_user (id) on delete cascade,
    visibility    varchar(8)   not null default 'SHARED',
    table_name    varchar(40)  not null,
    fields        jsonb        not null default '{}'::jsonb,
    field_clocks  jsonb        not null default '{}'::jsonb,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    deleted_at    timestamptz,
    constraint ck_vault_record_visibility check (visibility in ('SHARED', 'PRIVATE')),
    constraint ck_vault_record_table check (table_name ~ '^[a-z][a-z0-9_]{0,39}$')
);

create index ix_vault_record_household_table on vault_record (household_id, table_name);
create index ix_vault_record_deleted on vault_record (deleted_at) where deleted_at is not null;

-- The feed names the logical table with a vault: prefix so pull knows the row is ciphertext.
create function vault_sync_log_row() returns trigger language plpgsql as $$
begin
    insert into sync_log (household_id, table_name, row_id)
    values (new.household_id, 'vault:' || new.table_name, new.id);
    return new;
end $$;

create trigger trg_vault_record_sync_log
    after insert or update on vault_record
    for each row execute function vault_sync_log_row();

-- A snapshot is written once by the device that closed the month and never changed (BR-14).
create function vault_snapshot_immutable() returns trigger language plpgsql as $$
begin
    if old.table_name = 'net_worth_snapshot' then
        raise exception 'net_worth_snapshot is immutable';
    end if;
    return new;
end $$;

create trigger trg_vault_snapshot_immutable
    before update on vault_record
    for each row execute function vault_snapshot_immutable();

-- The legacy snapshot table must be emptied by the migration, so its delete guard goes; updates
-- stay refused.
drop trigger trg_net_worth_snapshot_immutable on net_worth_snapshot;
create trigger trg_net_worth_snapshot_immutable
    before update on net_worth_snapshot
    for each row execute function net_worth_snapshot_immutable();

-- Household rows as before, plus one exception: advisors (restricted sessions holding
-- REPORTS_VIEW, bound with app.reports = on) read the shared annual_report rows.
alter table vault_record enable row level security;
alter table vault_record force row level security;
create policy household_scope on vault_record
    using (current_setting('app.system', true) = 'on'
           or (household_id = app_household_id()
               and (owner_id = app_user_id()
                    or (visibility = 'SHARED' and coalesce(current_setting('app.restricted', true), '') <> 'on')
                    or (visibility = 'SHARED' and table_name = 'annual_report'
                        and coalesce(current_setting('app.reports', true), '') = 'on'))))
    with check (current_setting('app.system', true) = 'on' or household_id = app_household_id());

-- Reminders: the device decides what is due and when; the server only knows a time and a kind.
create table notification_schedule (
    id            uuid         primary key,
    user_id       uuid         not null references app_user (id) on delete cascade,
    household_id  uuid         not null references household (id) on delete cascade,
    kind          varchar(24)  not null,
    fire_at       timestamptz  not null,
    dedupe_key    varchar(160) not null,
    sent_at       timestamptz,
    constraint ck_notification_schedule_kind check (kind in ('BILL_DUE', 'PAY_DAY', 'OVERSPEND', 'MONTH_CLOSE'))
);

create index ix_notification_schedule_due on notification_schedule (fire_at) where sent_at is null;
create unique index ux_notification_schedule_once on notification_schedule (user_id, household_id, kind, dedupe_key);

-- Price alerts work on public quotes, so the server can still watch them for whoever opts in.
create table notification_preference (
    user_id         uuid     primary key references app_user (id) on delete cascade,
    metal_price     boolean  not null default false,
    metal_move_bp   integer  not null default 200,
    constraint ck_notification_preference_move check (metal_move_bp between 10 and 5000)
);

-- The plain tables are emptied household by household and member by member, so a parent row can
-- go before a child that someone else still has to migrate. Their foreign keys between each other
-- no longer protect anything and would block that.
do $$
declare fk record;
begin
    for fk in
        select c.conname, c.conrelid::regclass as child
        from pg_constraint c
        where c.contype = 'f'
          and exists (select 1 from pg_trigger t where t.tgrelid = c.conrelid and t.tgname like 'trg\_%\_sync\_log')
          and exists (select 1 from pg_trigger t where t.tgrelid = c.confrelid and t.tgname like 'trg\_%\_sync\_log')
          and c.conrelid <> 'vault_record'::regclass
    loop
        execute format('alter table %s drop constraint %I', fk.child, fk.conname);
    end loop;
end $$;
