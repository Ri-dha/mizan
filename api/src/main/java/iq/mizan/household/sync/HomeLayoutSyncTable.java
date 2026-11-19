package iq.mizan.household.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/**
 * Each member's choice of Home cards, their order, size and options (ADR home-1). Private to its
 * owner and sealed with the user key, so it follows them across devices without the household
 * or the server reading it.
 */
@Component
public class HomeLayoutSyncTable implements SyncTable {

    public static final String NAME = "home_layout";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "widgets", SyncColumnType.JSON,
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
