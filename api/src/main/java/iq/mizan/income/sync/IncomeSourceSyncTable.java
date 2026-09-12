package iq.mizan.income.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-INC-01. Matches V005 column for column. */
@Component
public class IncomeSourceSyncTable implements SyncTable {

    public static final String NAME = "income_source";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("frequency", SyncColumnType.TEXT),
            Map.entry("payDay", SyncColumnType.INTEGER),
            Map.entry("anchorDate", SyncColumnType.DATE),
            Map.entry("activeFrom", SyncColumnType.DATE),
            Map.entry("activeTo", SyncColumnType.DATE),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("sortOrder", SyncColumnType.INTEGER),
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
