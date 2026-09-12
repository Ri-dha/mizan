package iq.mizan.sync.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Reads and writes syncable rows for any registered table. Identifiers come only from the
 * registry, which has already refused anything that is not a plain lowercase name.
 */
@Repository
@AllArgsConstructor
public class SyncRowRepository {

    private static final TypeReference<Map<String, String>> CLOCKS = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public Optional<StoredRow> lockForUpdate(SyncTable table, UUID id) {
        return jdbcClient.sql("select %s from %s where id = ? for update".formatted(selectList(table), table.name()))
                .param(id)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(row -> toStoredRow(table, row));
    }

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

    public void insert(SyncTable table, UUID id, UUID householdId, UUID ownerId,
                       Map<String, Object> fields, Map<String, String> clocks) {
        List<String> columns = new ArrayList<>(List.of("id", "household_id", "owner_id", "field_clocks"));
        List<String> placeholders = new ArrayList<>(List.of("?", "?", "?", "?::jsonb"));
        List<Object> params = new ArrayList<>(List.of(id, householdId, ownerId, jsonMapper.writeValueAsString(clocks)));

        fields.forEach((field, value) -> {
            SyncColumnType type = table.fields().get(field);
            columns.add(SyncTableRegistry.columnFor(field));
            placeholders.add(type.placeholder());
            params.add(type.toJdbc(value, jsonMapper));
        });

        jdbcClient.sql("insert into %s (%s) values (%s)".formatted(
                        table.name(), String.join(", ", columns), String.join(", ", placeholders)))
                .params(params)
                .update();
    }

    public void update(SyncTable table, UUID id, Map<String, Object> fields, Map<String, String> clocks) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        fields.forEach((field, value) -> {
            SyncColumnType type = table.fields().get(field);
            assignments.add(SyncTableRegistry.columnFor(field) + " = " + type.placeholder());
            params.add(type.toJdbc(value, jsonMapper));
        });
        assignments.add("field_clocks = ?::jsonb");
        params.add(jsonMapper.writeValueAsString(clocks));
        assignments.add("updated_at = now()");
        params.add(id);

        jdbcClient.sql("update %s set %s where id = ?".formatted(table.name(), String.join(", ", assignments)))
                .params(params)
                .update();
    }

    private static String selectList(SyncTable table) {
        return "id, owner_id, field_clocks::text as field_clocks, " + table.fields().entrySet().stream()
                .map(entry -> entry.getValue().selectExpression(SyncTableRegistry.columnFor(entry.getKey())))
                .collect(Collectors.joining(", "));
    }

    private StoredRow toStoredRow(SyncTable table, Map<String, Object> row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        table.fields().forEach((field, type) ->
                fields.put(field, type.normalize(row.get(SyncTableRegistry.columnFor(field)), jsonMapper)));

        String clocksJson = (String) row.get("field_clocks");
        Map<String, String> clocks = clocksJson == null ? Map.of() : jsonMapper.readValue(clocksJson, CLOCKS);

        return new StoredRow((UUID) row.get("id"), (UUID) row.get("owner_id"), fields, clocks);
    }
}
