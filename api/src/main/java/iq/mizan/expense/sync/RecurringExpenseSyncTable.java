package iq.mizan.expense.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseSyncTable implements SyncTable {

    public static final String NAME = "recurring_expense";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("bucketId", SyncColumnType.UUID_VALUE),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("isEstimate", SyncColumnType.BOOLEAN),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("frequency", SyncColumnType.TEXT),
            Map.entry("dueDay", SyncColumnType.INTEGER),
            Map.entry("anchorDate", SyncColumnType.DATE),
            Map.entry("intervalDays", SyncColumnType.INTEGER),
            Map.entry("activeFrom", SyncColumnType.DATE),
            Map.entry("activeTo", SyncColumnType.DATE),
            Map.entry("category", SyncColumnType.TEXT),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("reminderDays", SyncColumnType.INTEGER),
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
