package iq.mizan.sync.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.sync.table.SyncColumnType;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes vault_record. The service has already checked every value it is given. */
@Repository
@AllArgsConstructor
public class VaultRowRepository {

    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> CLOCKS = new TypeReference<>() {
    };
    private static final String COLUMNS =
            "id, owner_id, table_name, visibility, deleted_at, fields::text as fields, field_clocks::text as field_clocks";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public Optional<VaultRow> lockForUpdate(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from vault_record where id = ? for update").param(id)
                .query().listOfRows().stream().findFirst().map(this::toRow);
    }

    public List<VaultRow> fetch(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COLUMNS + " from vault_record where id = any(?)").param(ids.toArray(UUID[]::new))
                .query().listOfRows().stream().map(this::toRow).toList();
    }

    public Optional<VaultRow> find(UUID id, String tableName) {
        return fetch(List.of(id)).stream().filter(row -> row.tableName().equals(tableName)).findFirst();
    }

    public void insert(UUID id, UUID householdId, UUID ownerId, String tableName, Map<String, Object> fields, Map<String, String> clocks) {
        jdbc.sql("insert into vault_record (id, household_id, owner_id, table_name, visibility, deleted_at, fields, field_clocks) "
                        + "values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)")
                .param(id).param(householdId).param(ownerId).param(tableName)
                .param(visibilityOf(fields)).param(deletedAtOf(fields))
                .param(json.writeValueAsString(cipherOnly(fields))).param(json.writeValueAsString(clocks))
                .update();
    }

    public void update(UUID id, Map<String, Object> fields, Map<String, String> clocks) {
        jdbc.sql("update vault_record set visibility = ?, deleted_at = ?, fields = ?::jsonb, field_clocks = ?::jsonb, updated_at = now() where id = ?")
                .param(visibilityOf(fields)).param(deletedAtOf(fields))
                .param(json.writeValueAsString(cipherOnly(fields))).param(json.writeValueAsString(clocks)).param(id)
                .update();
    }

    private static String visibilityOf(Map<String, Object> fields) {
        Object visibility = fields.get(VaultFields.VISIBILITY);
        return visibility == null ? VaultFields.SHARED : visibility.toString();
    }

    private static java.time.OffsetDateTime deletedAtOf(Map<String, Object> fields) {
        Object deletedAt = fields.get(VaultFields.DELETED_AT);
        return deletedAt == null ? null : Instant.parse(deletedAt.toString()).atOffset(ZoneOffset.UTC);
    }

    private static Map<String, Object> cipherOnly(Map<String, Object> fields) {
        Map<String, Object> cipher = new LinkedHashMap<>(fields);
        VaultFields.PLAIN.forEach(cipher::remove);
        return cipher;
    }

    private VaultRow toRow(Map<String, Object> row) {
        Map<String, Object> fields = new LinkedHashMap<>(json.readValue((String) row.get("fields"), FIELDS));
        fields.put(VaultFields.VISIBILITY, row.get("visibility"));
        fields.put(VaultFields.DELETED_AT, SyncColumnType.TIMESTAMP.normalize(row.get("deleted_at"), json));
        Map<String, String> clocks = json.readValue((String) row.get("field_clocks"), CLOCKS);
        return new VaultRow((UUID) row.get("id"), (UUID) row.get("owner_id"), (String) row.get("table_name"), fields, clocks);
    }
}
