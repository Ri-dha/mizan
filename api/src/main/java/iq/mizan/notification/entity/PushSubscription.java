package iq.mizan.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One browser's push endpoint (RFC 8030) with the keys that encrypt messages to it (RFC 8291). */
@Entity
@Table(name = "push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int failures;

    public static PushSubscription register(UUID userId, UUID householdId, String endpoint, String p256dh, String auth, String userAgent) {
        PushSubscription subscription = new PushSubscription();
        subscription.id = UUID.randomUUID();
        subscription.userId = userId;
        subscription.householdId = householdId;
        subscription.endpoint = endpoint;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        subscription.userAgent = userAgent;
        subscription.createdAt = Instant.now();
        return subscription;
    }

    public void rebind(UUID userId, UUID householdId, String p256dh, String auth) {
        this.userId = userId;
        this.householdId = householdId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.failures = 0;
    }

    public void recordFailure() {
        failures++;
    }

    public void recordSuccess() {
        failures = 0;
    }
}
