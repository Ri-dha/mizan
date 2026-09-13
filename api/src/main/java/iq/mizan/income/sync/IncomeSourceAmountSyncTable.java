package iq.mizan.income.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class IncomeSourceAmountSyncTable implements SyncTable {

    public static final String NAME = "income_source_amount";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "incomeSourceId", SyncColumnType.UUID_VALUE,
            "effectiveFrom", SyncColumnType.DATE,
            "amount", SyncColumnType.BIGINT,
            "currency", SyncColumnType.TEXT,
            "fxRateMicros", SyncColumnType.BIGINT,
            "note", SyncColumnType.TEXT,
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
