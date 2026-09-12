package iq.mizan.common.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private AuditEntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private String afterState;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    static AuditEntry from(AuditRecord record, String beforeJson, String afterJson) {
        AuditEntry entry = new AuditEntry();
        entry.id = UUID.randomUUID();
        entry.householdId = record.householdId();
        entry.actorId = record.actorId();
        entry.entityType = record.entityType();
        entry.entityId = record.entityId();
        entry.action = record.action();
        entry.beforeState = beforeJson;
        entry.afterState = afterJson;
        entry.ipAddress = record.ipAddress();
        entry.occurredAt = Instant.now();
        return entry;
    }
}
