package iq.mizan.asset.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-AST-03: the valuation history behind an asset's current value. */
@Component
public class AssetValuationSyncTable implements SyncTable {

    public static final String NAME = "asset_valuation";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "assetId", SyncColumnType.UUID_VALUE,
            "valuedOn", SyncColumnType.DATE,
            "value", SyncColumnType.BIGINT,
            "source", SyncColumnType.TEXT,
            "note", SyncColumnType.TEXT,
            "deletedAt", SyncColumnType.TIMESTAMP);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }
}
