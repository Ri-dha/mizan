package iq.mizan.metal.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MetalDisposalSyncTable implements SyncTable {

    public static final String NAME = "metal_disposal";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("metal", SyncColumnType.TEXT),
            Map.entry("soldOn", SyncColumnType.DATE),
            Map.entry("weightMg", SyncColumnType.BIGINT),
            Map.entry("proceeds", SyncColumnType.BIGINT),
            Map.entry("fees", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("method", SyncColumnType.TEXT),
            Map.entry("buyer", SyncColumnType.TEXT),
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
