package iq.mizan.asset.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class CashAdjustmentSyncTable implements SyncTable {

    public static final String NAME = "cash_adjustment";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("cashAccountId", SyncColumnType.UUID_VALUE),
            Map.entry("adjustedOn", SyncColumnType.DATE),
            Map.entry("previousBalance", SyncColumnType.BIGINT),
            Map.entry("newBalance", SyncColumnType.BIGINT),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }
}
