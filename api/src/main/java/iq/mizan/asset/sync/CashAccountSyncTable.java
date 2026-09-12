package iq.mizan.asset.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-AST-08. Matches V004__cash_accounts.sql column for column. */
@Component
public class CashAccountSyncTable implements SyncTable {

    public static final String NAME = "cash_account";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "name", SyncColumnType.TEXT,
            "kind", SyncColumnType.TEXT,
            "institution", SyncColumnType.TEXT,
            "balance", SyncColumnType.BIGINT,
            "currency", SyncColumnType.TEXT,
            "sortOrder", SyncColumnType.INTEGER,
            "deletedAt", SyncColumnType.TIMESTAMP);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }
}
