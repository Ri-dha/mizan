package iq.mizan.common.audit;

import java.util.Map;
import java.util.UUID;

import lombok.Builder;

@Builder
public record AuditRecord(
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        UUID actorId,
        UUID householdId,
        Map<String, Object> before,
        Map<String, Object> after,
        String ipAddress) {

    public static AuditRecordBuilder of(AuditAction action, AuditEntityType entityType) {
        return AuditRecord.builder().action(action).entityType(entityType);
    }
}
