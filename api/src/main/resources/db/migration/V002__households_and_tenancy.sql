-- Households and the row-level security scaffolding every household table uses (§4.2, BR-16).

create table household (
    id              uuid        primary key,
    name            varchar(80) not null,
    base_currency   varchar(3)  not null,
    month_start_day integer     not null default 1,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint ck_household_month_start_day check (month_start_day between 1 and 28)
);

create table membership (
    id           uuid        primary key,
    household_id uuid        not null references household (id) on delete cascade,
    user_id      uuid        not null references app_user (id) on delete cascade,
    role         varchar(16) not null,
    status       varchar(16) not null,
    joined_at    timestamptz not null default now()
);

create unique index ux_membership_household_user on membership (household_id, user_id);
create index ix_membership_user on membership (user_id);

-- The application switches to this role for every request transaction. It is not the table
-- owner and not a superuser, so row-level security actually applies to it; the connection
-- user (which owns the schema and runs migrations) would bypass every policy.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'mizan_app') then
        create role mizan_app nologin;
    end if;
end $$;

grant mizan_app to current_user;
grant usage on schema public to mizan_app;
grant select, insert, update, delete on all tables in schema public to mizan_app;
grant usage, select on all sequences in schema public to mizan_app;
alter default privileges in schema public grant select, insert, update, delete on tables to mizan_app;
alter default privileges in schema public grant usage, select on sequences to mizan_app;

create function app_user_id() returns uuid language sql stable as $$
    select nullif(current_setting('app.user_id', true), '')::uuid
$$;

create function app_household_id() returns uuid language sql stable as $$
    select nullif(current_setting('app.household_id', true), '')::uuid
$$;

-- Household scoping for tables that carry household_id, and the private-record rule for
-- those that also carry owner_id + visibility (BR-16). Nothing is visible without a bound
-- household, so a request that forgot to bind sees an empty database rather than everyone's.
create procedure apply_household_rls(tbl regclass, with_visibility boolean)
language plpgsql as $$
begin
    execute format('alter table %s enable row level security', tbl);
    execute format('alter table %s force row level security', tbl);
    if with_visibility then
        execute format($p$
            create policy household_scope on %s
                using (household_id = app_household_id()
                       and (visibility = 'SHARED' or owner_id = app_user_id()))
                with check (household_id = app_household_id())
        $p$, tbl);
    else
        execute format($p$
            create policy household_scope on %s
                using (household_id = app_household_id())
                with check (household_id = app_household_id())
        $p$, tbl);
    end if;
end $$;
