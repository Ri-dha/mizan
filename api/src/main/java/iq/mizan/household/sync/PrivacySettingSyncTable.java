package iq.mizan.household.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** BR-16: each member's default visibility per record type; the row is private to its owner. */
@Component
public class PrivacySettingSyncTable implements SyncTable {

    public static final String NAME = "privacy_setting";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "transactions", SyncColumnType.TEXT,
            "accounts", SyncColumnType.TEXT,
            "metals", SyncColumnType.TEXT,
            "debts", SyncColumnType.TEXT,
            "goals", SyncColumnType.TEXT,
            "assets", SyncColumnType.TEXT,
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
