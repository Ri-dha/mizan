package iq.mizan.debt.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class DebtSyncTable implements SyncTable {

    public static final String NAME = "debt";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("counterparty", SyncColumnType.TEXT),
            Map.entry("direction", SyncColumnType.TEXT),
            Map.entry("principal", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("annualRateBasisPoints", SyncColumnType.INTEGER),
            Map.entry("termMonths", SyncColumnType.INTEGER),
            Map.entry("monthlyPayment", SyncColumnType.BIGINT),
            Map.entry("bucketId", SyncColumnType.UUID_VALUE),
            Map.entry("startDate", SyncColumnType.DATE),
            Map.entry("status", SyncColumnType.TEXT),
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
