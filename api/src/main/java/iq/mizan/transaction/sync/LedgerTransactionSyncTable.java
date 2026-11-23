package iq.mizan.transaction.sync;

import java.util.HashMap;
import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class LedgerTransactionSyncTable implements SyncTable {

    public static final String NAME = "ledger_transaction";

    /** The fields from before ADR e2e-1; later additions are listed separately. */
    private static final Map<String, SyncColumnType> BASE_FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("type", SyncColumnType.TEXT),
            Map.entry("occurredOn", SyncColumnType.DATE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("currency", SyncColumnType.TEXT),
            Map.entry("fxRateMicros", SyncColumnType.BIGINT),
            Map.entry("baseAmount", SyncColumnType.BIGINT),
            Map.entry("bucketId", SyncColumnType.UUID_VALUE),
            Map.entry("category", SyncColumnType.TEXT),
            Map.entry("payee", SyncColumnType.TEXT),
            Map.entry("note", SyncColumnType.TEXT),
            Map.entry("counterpartyType", SyncColumnType.TEXT),
            Map.entry("counterpartyId", SyncColumnType.UUID_VALUE),
            Map.entry("attachmentId", SyncColumnType.UUID_VALUE),
            Map.entry("splitGroupId", SyncColumnType.UUID_VALUE),
            Map.entry("assetId", SyncColumnType.UUID_VALUE),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    /** T-cash-movements: the cash account an expense was paid from or income went into. */
    private static final String CASH_ACCOUNT_ID = "cashAccountId";

    /** T-cash-movements: what reached the other account of a transfer, in its currency (an exchange). */
    private static final String COUNTER_AMOUNT = "counterAmount";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        Map<String, SyncColumnType> fields = new HashMap<>(BASE_FIELDS);
        fields.put(CASH_ACCOUNT_ID, SyncColumnType.UUID_VALUE);
        fields.put(COUNTER_AMOUNT, SyncColumnType.BIGINT);
        return Map.copyOf(fields);
    }

}
