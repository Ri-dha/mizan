package iq.mizan.common.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The operator's view of the audit log (ADR admin-1); entries hold field names and states, never record values. */
@Service
@AllArgsConstructor
public class AuditSearch {

    public static final int MAX_PAGE = 200;

    public record Entry(UUID id, Instant occurredAt, String action, String entityType, UUID entityId, UUID actorId, UUID householdId,
                        String before, String after, String ipAddress) {
    }

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<Entry> search(String action, UUID actorId, UUID entityId, Instant before, int limit) {
        StringBuilder sql = new StringBuilder("select id, occurred_at, action, entity_type, entity_id, actor_id, household_id, "
                + "before_state::text as before_state, after_state::text as after_state, ip_address from audit_entry where occurred_at < ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.from(before == null ? Instant.now().plusSeconds(1) : before));
        if (action != null && !action.isBlank()) {
            sql.append(" and action = ?");
            params.add(action.trim());
        }
        if (actorId != null) {
            sql.append(" and actor_id = ?");
            params.add(actorId);
        }
        if (entityId != null) {
            sql.append(" and entity_id = ?");
            params.add(entityId);
        }
        sql.append(" order by occurred_at desc limit ?");
        params.add(Math.clamp(limit, 1, MAX_PAGE));
        return jdbc.sql(sql.toString()).params(params).query((rs, i) -> new Entry(
                rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(), rs.getString("action"),
                rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), rs.getObject("actor_id", UUID.class),
                rs.getObject("household_id", UUID.class), rs.getString("before_state"), rs.getString("after_state"),
                rs.getString("ip_address"))).list();
    }
}
