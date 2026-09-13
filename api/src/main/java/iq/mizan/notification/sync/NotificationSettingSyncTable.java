package iq.mizan.notification.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-NTF-06: each member's switches and thresholds, private to them, applied by the daily job. */
@Component
public class NotificationSettingSyncTable implements SyncTable {

    public static final String NAME = "notification_setting";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("billDue", SyncColumnType.BOOLEAN),
            Map.entry("billLeadDays", SyncColumnType.INTEGER),
            Map.entry("payDay", SyncColumnType.BOOLEAN),
            Map.entry("overspend", SyncColumnType.BOOLEAN),
            Map.entry("overspendThresholdBp", SyncColumnType.INTEGER),
            Map.entry("monthClose", SyncColumnType.BOOLEAN),
            Map.entry("metalPrice", SyncColumnType.BOOLEAN),
            Map.entry("metalMoveBp", SyncColumnType.INTEGER),
            Map.entry("quietMode", SyncColumnType.BOOLEAN),
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
