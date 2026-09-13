package iq.mizan.sync.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import iq.mizan.sync.table.SyncTable;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The write path for rows the server originates (seeded defaults, imports); devices pull them like any change. */
@Service
@AllArgsConstructor
public class SyncWriter {

    private final SyncRowRepository rows;
    private final ServerClock serverClock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(SyncTable table, UUID rowId, UUID householdId, UUID ownerId, Map<String, Object> fields) {
        rows.insert(table, rowId, householdId, ownerId, fields, stamped(fields));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(SyncTable table, UUID rowId, Map<String, Object> fields) {
        StoredRow stored = rows.lockForUpdate(table, rowId).orElseThrow();
        Map<String, String> clocks = new java.util.LinkedHashMap<>(stored.clocks());
        clocks.putAll(stamped(fields));
        rows.update(table, rowId, fields, clocks);
    }

    private Map<String, String> stamped(Map<String, Object> fields) {
        String stamp = serverClock.next();
        return fields.keySet().stream().collect(Collectors.toMap(Function.identity(), field -> stamp));
    }
}
