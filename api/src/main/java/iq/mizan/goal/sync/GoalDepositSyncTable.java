package iq.mizan.goal.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class GoalDepositSyncTable implements SyncTable {

    public static final String NAME = "goal_deposit";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("goalId", SyncColumnType.UUID_VALUE),
            Map.entry("depositedOn", SyncColumnType.DATE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("direction", SyncColumnType.TEXT),
            Map.entry("transactionId", SyncColumnType.UUID_VALUE),
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
