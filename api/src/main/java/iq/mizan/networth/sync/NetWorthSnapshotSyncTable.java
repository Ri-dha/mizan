package iq.mizan.networth.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class NetWorthSnapshotSyncTable implements SyncTable {

    public static final String NAME = "net_worth_snapshot";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("takenAt", SyncColumnType.TIMESTAMP),
            Map.entry("totalAssets", SyncColumnType.BIGINT),
            Map.entry("totalLiabilities", SyncColumnType.BIGINT),
            Map.entry("netWorth", SyncColumnType.BIGINT),
            Map.entry("liquidAssets", SyncColumnType.BIGINT),
            Map.entry("illiquidAssets", SyncColumnType.BIGINT),
            Map.entry("composition", SyncColumnType.JSON),
            Map.entry("rateSet", SyncColumnType.JSON),
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
