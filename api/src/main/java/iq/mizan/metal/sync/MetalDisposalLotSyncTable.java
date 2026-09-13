package iq.mizan.metal.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MetalDisposalLotSyncTable implements SyncTable {

    public static final String NAME = "metal_disposal_lot";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("disposalId", SyncColumnType.UUID_VALUE),
            Map.entry("lotId", SyncColumnType.UUID_VALUE),
            Map.entry("weightMg", SyncColumnType.BIGINT),
            Map.entry("metalCost", SyncColumnType.BIGINT),
            Map.entry("makingCharge", SyncColumnType.BIGINT),
            Map.entry("fees", SyncColumnType.BIGINT),
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
