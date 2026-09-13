package iq.mizan.asset.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-AST-01..05. Matches V013__assets.sql column for column. */
@Component
public class AssetSyncTable implements SyncTable {

    public static final String NAME = "asset";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("type", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("purchaseDate", SyncColumnType.DATE),
            Map.entry("purchasePrice", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("liquidity", SyncColumnType.TEXT),
            Map.entry("depreciationMethod", SyncColumnType.TEXT),
            Map.entry("annualRateBasisPoints", SyncColumnType.INTEGER),
            Map.entry("salvageValue", SyncColumnType.BIGINT),
            Map.entry("attributes", SyncColumnType.JSON),
            Map.entry("status", SyncColumnType.TEXT),
            Map.entry("soldOn", SyncColumnType.DATE),
            Map.entry("salePrice", SyncColumnType.BIGINT),
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
