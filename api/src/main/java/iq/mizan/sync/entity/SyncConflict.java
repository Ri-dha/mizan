package iq.mizan.sync.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A client edit that lost to a newer server value (FR-TRX-07's conflict log). */
@Entity
@Table(name = "sync_conflict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncConflict {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "row_id", nullable = false)
    private UUID rowId;

    @Column(nullable = false)
    private String field;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_value")
    private String clientValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "server_value")
    private String serverValue;

    @Column(name = "client_clock", nullable = false)
    private String clientClock;

    @Column(name = "server_clock", nullable = false)
    private String serverClock;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Builder
    private SyncConflict(
            UUID householdId, UUID deviceId, String tableName, UUID rowId, String field,
            String clientValue, String serverValue, String clientClock, String serverClock) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.deviceId = deviceId;
        this.tableName = tableName;
        this.rowId = rowId;
        this.field = field;
        this.clientValue = clientValue;
        this.serverValue = serverValue;
        this.clientClock = clientClock;
        this.serverClock = serverClock;
        this.detectedAt = Instant.now();
    }
}
