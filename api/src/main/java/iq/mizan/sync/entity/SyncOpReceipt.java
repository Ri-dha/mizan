package iq.mizan.sync.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Makes a push idempotent: an op id seen before is acknowledged, not applied twice. */
@Entity
@Table(name = "sync_op_receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncOpReceipt {

    @Id
    @Column(name = "op_id")
    private UUID opId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    public static SyncOpReceipt of(UUID opId, UUID deviceId) {
        SyncOpReceipt receipt = new SyncOpReceipt();
        receipt.opId = opId;
        receipt.deviceId = deviceId;
        receipt.appliedAt = Instant.now();
        return receipt;
    }
}
