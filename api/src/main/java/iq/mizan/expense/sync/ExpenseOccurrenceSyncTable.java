package iq.mizan.expense.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class ExpenseOccurrenceSyncTable implements SyncTable {

    public static final String NAME = "expense_occurrence";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("recurringExpenseId", SyncColumnType.UUID_VALUE),
            Map.entry("dueDate", SyncColumnType.DATE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("expectedAmount", SyncColumnType.BIGINT),
            Map.entry("actualAmount", SyncColumnType.BIGINT),
            Map.entry("paidOn", SyncColumnType.DATE),
            Map.entry("transactionId", SyncColumnType.UUID_VALUE),
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
