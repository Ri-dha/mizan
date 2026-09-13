-- v3 refinement: transaction splits and running costs (FR-TRX-06, FR-AST-07), dealer buy-back
-- valuation (FR-MTL-11), dependent and advisor roles (§4.2) with expiring access, and the
-- restricted row-level view a dependent gets: their own records only, never the shared ones.

alter table ledger_transaction add column split_group_id uuid;
alter table ledger_transaction add column asset_id uuid references asset (id) deferrable initially deferred;
create index ix_ledger_transaction_split on ledger_transaction (split_group_id) where split_group_id is not null;
create index ix_ledger_transaction_asset on ledger_transaction (asset_id) where asset_id is not null;

alter table market_setting add column valuation_basis varchar(8) not null default 'MARKET';
alter table market_setting add column gold_buyback_basis_points integer not null default 0;
alter table market_setting add column silver_buyback_basis_points integer not null default 0;
alter table market_setting add constraint ck_market_setting_basis check (valuation_basis in ('MARKET', 'BUYBACK'));
alter table market_setting add constraint ck_market_setting_buyback check (
    gold_buyback_basis_points between 0 and 5000 and silver_buyback_basis_points between 0 and 5000);

alter table membership add column expires_at timestamptz;
alter table household_invitation drop constraint ck_household_invitation_role;
alter table household_invitation add constraint ck_household_invitation_role check (role in ('MEMBER', 'VIEWER', 'DEPENDENT', 'ADVISOR'));
alter table household_invitation add column access_days integer;

-- A session that lacks HOUSEHOLD_VIEW is bound with app.restricted = 'on' and sees only what it owns.
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
                           and (owner_id = app_user_id()
                                or (visibility = 'SHARED' and coalesce(current_setting('app.restricted', true), '') <> 'on'))))
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
declare t record;
begin
    for t in
        select p.tablename
        from pg_policies p
        join information_schema.columns c on c.table_name = p.tablename and c.column_name = 'visibility' and c.table_schema = 'public'
        where p.policyname = 'household_scope' and p.schemaname = 'public'
    loop
        call apply_household_rls(t.tablename::regclass, true);
    end loop;
end $$;
