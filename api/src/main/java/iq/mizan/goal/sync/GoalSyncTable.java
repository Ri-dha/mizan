package iq.mizan.goal.sync;

import java.util.HashMap;
import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class GoalSyncTable implements SyncTable {

    public static final String NAME = "goal";

    /** The fields from before ADR e2e-1; later additions are listed separately. */
    private static final Map<String, SyncColumnType> BASE_FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("targetAmount", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("targetDate", SyncColumnType.DATE),
            Map.entry("monthlyContribution", SyncColumnType.BIGINT),
            Map.entry("bucketId", SyncColumnType.UUID_VALUE),
            Map.entry("backingAssetType", SyncColumnType.TEXT),
            Map.entry("backingAssetId", SyncColumnType.UUID_VALUE),
            Map.entry("status", SyncColumnType.TEXT),
            Map.entry("completedOn", SyncColumnType.DATE),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("sortOrder", SyncColumnType.INTEGER),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    /** T-goal-opening: money already set aside before the goal was added, in the goal's currency. */
    private static final String OPENING_AMOUNT = "openingAmount";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        Map<String, SyncColumnType> fields = new HashMap<>(BASE_FIELDS);
        fields.put(OPENING_AMOUNT, SyncColumnType.BIGINT);
        return Map.copyOf(fields);
    }

}
