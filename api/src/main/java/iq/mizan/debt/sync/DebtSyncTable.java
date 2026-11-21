package iq.mizan.debt.sync;

import java.util.HashMap;
import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class DebtSyncTable implements SyncTable {

    public static final String NAME = "debt";

    /** The plain table's columns from before ADR e2e-1; fields added since exist only sealed. */
    private static final Map<String, SyncColumnType> LEGACY_FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("name", SyncColumnType.TEXT),
            Map.entry("counterparty", SyncColumnType.TEXT),
            Map.entry("direction", SyncColumnType.TEXT),
            Map.entry("principal", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("annualRateBasisPoints", SyncColumnType.INTEGER),
            Map.entry("termMonths", SyncColumnType.INTEGER),
            Map.entry("monthlyPayment", SyncColumnType.BIGINT),
            Map.entry("bucketId", SyncColumnType.UUID_VALUE),
            Map.entry("startDate", SyncColumnType.DATE),
            Map.entry("status", SyncColumnType.TEXT),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    /** T-debt-accounts: the cash account the money came from (lent) or went into (borrowed). */
    private static final String CASH_ACCOUNT_ID = "cashAccountId";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        Map<String, SyncColumnType> fields = new HashMap<>(LEGACY_FIELDS);
        fields.put(CASH_ACCOUNT_ID, SyncColumnType.UUID_VALUE);
        return Map.copyOf(fields);
    }

    @Override
    public Map<String, SyncColumnType> legacyFields() {
        return LEGACY_FIELDS;
    }
}
