package iq.mizan.plan.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-PLN-06: one row per effective-dated plan version. */
@Component
public class PlanSyncTable implements SyncTable {

    public static final String NAME = "plan";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "effectiveFrom", SyncColumnType.TEXT,
            "effectiveTo", SyncColumnType.TEXT,
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
