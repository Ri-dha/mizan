package iq.mizan.metal.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MetalLotSyncTable implements SyncTable {

    public static final String NAME = "metal_lot";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("metal", SyncColumnType.TEXT),
            Map.entry("purityLabel", SyncColumnType.TEXT),
            Map.entry("purityBasisPoints", SyncColumnType.INTEGER),
            Map.entry("weightMg", SyncColumnType.BIGINT),
            Map.entry("weightUnitEntered", SyncColumnType.TEXT),
            Map.entry("quantityEntered", SyncColumnType.TEXT),
            Map.entry("purchaseDate", SyncColumnType.DATE),
            Map.entry("metalCost", SyncColumnType.BIGINT),
            Map.entry("makingCharge", SyncColumnType.BIGINT),
            Map.entry("fees", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("form", SyncColumnType.TEXT),
            Map.entry("dealer", SyncColumnType.TEXT),
            Map.entry("location", SyncColumnType.TEXT),
            Map.entry("serial", SyncColumnType.TEXT),
            Map.entry("heldFor", SyncColumnType.TEXT),
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
