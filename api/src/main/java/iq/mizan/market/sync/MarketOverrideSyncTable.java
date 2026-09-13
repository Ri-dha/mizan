package iq.mizan.market.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MarketOverrideSyncTable implements SyncTable {

    public static final String NAME = "market_override";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("instrument", SyncColumnType.TEXT),
            Map.entry("priceMicros", SyncColumnType.BIGINT),
            Map.entry("effectiveFrom", SyncColumnType.DATE),
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
