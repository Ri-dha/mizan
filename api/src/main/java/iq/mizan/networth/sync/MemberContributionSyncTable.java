package iq.mizan.networth.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/**
 * BR-16 under end-to-end encryption: each member's device publishes the total of that member's
 * private records, encrypted with the household key, so the household figure includes them
 * without listing them.
 */
@Component
public class MemberContributionSyncTable implements SyncTable {

    public static final String NAME = "member_contribution";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "memberId", SyncColumnType.UUID_VALUE,
            "totalAssets", SyncColumnType.BIGINT,
            "totalLiabilities", SyncColumnType.BIGINT,
            "composition", SyncColumnType.JSON,
            "computedAt", SyncColumnType.TIMESTAMP,
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
