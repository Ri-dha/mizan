package iq.mizan.debt.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class DebtPaymentSyncTable implements SyncTable {

    public static final String NAME = "debt_payment";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("debtId", SyncColumnType.UUID_VALUE),
            Map.entry("paidOn", SyncColumnType.DATE),
            Map.entry("monthKey", SyncColumnType.TEXT),
            Map.entry("amount", SyncColumnType.BIGINT),
            Map.entry("interestComponent", SyncColumnType.BIGINT),
            Map.entry("principalComponent", SyncColumnType.BIGINT),
            Map.entry("transactionId", SyncColumnType.UUID_VALUE),
            Map.entry("note", SyncColumnType.TEXT),
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
