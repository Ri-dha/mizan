package iq.mizan.networth.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MonthCloseSyncTable implements SyncTable {

    public static final String NAME = "month_close";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("snapshotId", SyncColumnType.UUID_VALUE),
            Map.entry("closedAt", SyncColumnType.TIMESTAMP),
            Map.entry("reopenedAt", SyncColumnType.TIMESTAMP),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }

    @Override
    public boolean readOnly() {
        return true;
    }
}
