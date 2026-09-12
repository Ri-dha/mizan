package iq.mizan.income.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-INC-02, FR-INC-03. The FX rate and base amount are frozen at receipt time (BR-09). */
@Component
public class IncomeReceiptSyncTable implements SyncTable {

    public static final String NAME = "income_receipt";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("incomeSourceId", SyncColumnType.UUID_VALUE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("receivedOn", SyncColumnType.DATE),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("baseAmount", SyncColumnType.BIGINT),
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
