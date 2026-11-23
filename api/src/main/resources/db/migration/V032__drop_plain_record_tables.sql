-- ADR e2e-1 left the plain record tables in place so devices could pull and encrypt their old
-- rows. Every record now lives in vault_record; a database that still holds a plain row has not
-- finished that migration, so this stops rather than lose it.
do $$
declare
    tbl text;
    remaining bigint;
begin
    foreach tbl in array array[
        'asset', 'asset_valuation', 'attachment', 'bucket', 'bucket_move', 'cash_account',
        'cash_adjustment', 'debt', 'debt_payment', 'expense_occurrence', 'goal', 'goal_deposit',
        'income_receipt', 'income_source', 'income_source_amount', 'ledger_transaction',
        'market_override', 'market_setting', 'metal_disposal', 'metal_disposal_lot', 'metal_lot',
        'month_close', 'net_worth_snapshot', 'notification_setting', 'plan', 'privacy_setting',
        'recurring_expense']
    loop
        execute format('select count(*) from %I', tbl) into remaining;
        if remaining > 0 then
            raise exception 'Table % still has % plain rows; finish the vault migration first', tbl, remaining;
        end if;
    end loop;
end
$$;

delete from sync_log where table_name not like 'vault:%';

drop table asset, asset_valuation, attachment, bucket, bucket_move, cash_account, cash_adjustment,
    debt, debt_payment, expense_occurrence, goal, goal_deposit, income_receipt, income_source,
    income_source_amount, ledger_transaction, market_override, market_setting, metal_disposal,
    metal_disposal_lot, metal_lot, month_close, net_worth_snapshot, notification_setting, plan,
    privacy_setting, recurring_expense;

drop procedure make_syncable(regclass);
drop function sync_log_row();
drop function net_worth_snapshot_immutable();
