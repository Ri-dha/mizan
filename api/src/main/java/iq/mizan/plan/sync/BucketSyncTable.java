package iq.mizan.plan.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-PLN-01. */
@Component
public class BucketSyncTable implements SyncTable {

    public static final String NAME = "bucket";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("planId", SyncColumnType.UUID_VALUE),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("colour", SyncColumnType.TEXT),
            Map.entry("shareBasisPoints", SyncColumnType.INTEGER),
            Map.entry("fixedAmount", SyncColumnType.BIGINT),
            Map.entry("carryOver", SyncColumnType.BOOLEAN),
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
