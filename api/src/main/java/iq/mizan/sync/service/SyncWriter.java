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
        String stamp = serverClock.next();
        Map<String, String> clocks = fields.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), field -> stamp));
        rows.insert(table, rowId, householdId, ownerId, fields, clocks);
    }
}
