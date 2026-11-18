package iq.mizan.sync.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;
import iq.mizan.sync.table.SyncTableRegistry;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads legacy plain rows so devices can pull and encrypt them during the migration to
 * vault_record (ADR e2e-1). Nothing writes these tables any more. Identifiers come only from the
 * registry, which has already refused anything that is not a plain lowercase name.
 */
@Repository
@AllArgsConstructor
public class SyncRowRepository {

    private static final TypeReference<Map<String, String>> CLOCKS = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public List<StoredRow> fetch(SyncTable table, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("select %s from %s where id = any(?)".formatted(selectList(table), table.name()))
                .param(ids.toArray(UUID[]::new))
                .query()
                .listOfRows()
                .stream()
                .map(row -> toStoredRow(table, row))
                .toList();
    }

    private static String selectList(SyncTable table) {
        return "id, owner_id, field_clocks::text as field_clocks, " + table.legacyFields().entrySet().stream()
                .map(entry -> entry.getValue().selectExpression(SyncTableRegistry.columnFor(entry.getKey())))
                .collect(Collectors.joining(", "));
    }

    private StoredRow toStoredRow(SyncTable table, Map<String, Object> row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        table.legacyFields().forEach((field, type) ->
                fields.put(field, type.normalize(row.get(SyncTableRegistry.columnFor(field)), jsonMapper)));

        String clocksJson = (String) row.get("field_clocks");
        Map<String, String> clocks = clocksJson == null ? Map.of() : jsonMapper.readValue(clocksJson, CLOCKS);

        return new StoredRow((UUID) row.get("id"), (UUID) row.get("owner_id"), fields, clocks);
    }
}
