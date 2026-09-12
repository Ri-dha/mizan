package iq.mizan.goal.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class GoalSyncTable implements SyncTable {

    public static final String NAME = "goal";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
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

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }
}
