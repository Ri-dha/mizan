package iq.mizan.income.sync;

import java.util.HashMap;
import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

/** FR-INC-02, FR-INC-03. The FX rate and base amount are frozen at receipt time (BR-09). */
@Component
public class IncomeReceiptSyncTable implements SyncTable {

    public static final String NAME = "income_receipt";

    /** The plain table's columns from before ADR e2e-1; fields added since exist only sealed. */
    private static final Map<String, SyncColumnType> LEGACY_FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("incomeSourceId", SyncColumnType.UUID_VALUE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("receivedOn", SyncColumnType.DATE),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("baseAmount", SyncColumnType.BIGINT),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    /** T-cash-movements: which cash accounts the money went into, and how much each; a list, sealed. */
    private static final String INTO_ACCOUNTS = "intoAccounts";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        Map<String, SyncColumnType> fields = new HashMap<>(LEGACY_FIELDS);
        fields.put(INTO_ACCOUNTS, SyncColumnType.TEXT);
        return Map.copyOf(fields);
    }

    @Override
    public Map<String, SyncColumnType> legacyFields() {
        return LEGACY_FIELDS;
    }
}
