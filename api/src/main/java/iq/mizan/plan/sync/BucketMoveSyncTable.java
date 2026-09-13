package iq.mizan.plan.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-PLN-03. Matches V014__bucket_moves.sql column for column. */
@Component
public class BucketMoveSyncTable implements SyncTable {

    public static final String NAME = "bucket_move";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "fromBucketId", SyncColumnType.UUID_VALUE,
            "toBucketId", SyncColumnType.UUID_VALUE,
            "monthKey", SyncColumnType.TEXT,
            "movedOn", SyncColumnType.DATE,
            "amount", SyncColumnType.BIGINT,
            "reason", SyncColumnType.TEXT,
            "transactionId", SyncColumnType.UUID_VALUE,
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
