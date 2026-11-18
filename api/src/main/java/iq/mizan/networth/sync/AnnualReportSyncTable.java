package iq.mizan.networth.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/**
 * A year's totals computed on a member's device and encrypted with the report key, so advisors
 * can read the report without the household key (§4.2 P4, ADR e2e-1).
 */
@Component
public class AnnualReportSyncTable implements SyncTable {

    public static final String NAME = "annual_report";

    private static final Map<String, SyncColumnType> FIELDS = Map.of(
            "visibility", SyncColumnType.TEXT,
            "year", SyncColumnType.INTEGER,
            "report", SyncColumnType.JSON,
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
