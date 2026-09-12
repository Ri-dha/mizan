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

@Entity
@Table(name = "sync_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncDevice {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_pull_seq", nullable = false)
    private long lastPullSeq;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SyncDevice register(UUID householdId, UUID userId, String name) {
        SyncDevice device = new SyncDevice();
        device.id = UUID.randomUUID();
        device.householdId = householdId;
        device.userId = userId;
        device.name = name;
        device.createdAt = Instant.now();
        device.lastSeenAt = device.createdAt;
        return device;
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    public void recordPull(long seq) {
        lastPullSeq = Math.max(lastPullSeq, seq);
        lastSeenAt = Instant.now();
    }

    public void recordPush() {
        lastSeenAt = Instant.now();
    }
}
