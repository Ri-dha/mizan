package iq.mizan.transaction.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class LedgerTransactionSyncTable implements SyncTable {

    public static final String NAME = "ledger_transaction";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
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
