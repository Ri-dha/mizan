package iq.mizan.market.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class MarketSettingSyncTable implements SyncTable {

    public static final String NAME = "market_setting";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("rateKind", SyncColumnType.TEXT),
            Map.entry("goldPremiumBasisPoints", SyncColumnType.INTEGER),
            Map.entry("silverPremiumBasisPoints", SyncColumnType.INTEGER),
            Map.entry("goldMethod", SyncColumnType.TEXT),
            Map.entry("silverMethod", SyncColumnType.TEXT),
            Map.entry("valuationBasis", SyncColumnType.TEXT),
            Map.entry("priceSource", SyncColumnType.TEXT),
            Map.entry("goldBuybackBasisPoints", SyncColumnType.INTEGER),
            Map.entry("silverBuybackBasisPoints", SyncColumnType.INTEGER),
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
