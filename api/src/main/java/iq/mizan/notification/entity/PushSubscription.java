package iq.mizan.notification.entity;

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

/**
 * One device: a browser's push endpoint (RFC 8030) with the keys that encrypt messages to it
 * (RFC 8291), or a phone's Expo push token, kept in {@code endpoint} (ADR push-1).
 */
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PushChannel channel;

    private String p256dh;

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
        subscription.channel = PushChannel.WEB_PUSH;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        subscription.userAgent = userAgent;
        subscription.createdAt = Instant.now();
        return subscription;
    }

    public static PushSubscription registerPhone(UUID userId, UUID householdId, String pushToken, String userAgent) {
        PushSubscription subscription = new PushSubscription();
        subscription.id = UUID.randomUUID();
        subscription.userId = userId;
        subscription.householdId = householdId;
        subscription.endpoint = pushToken;
        subscription.channel = PushChannel.EXPO;
        subscription.userAgent = userAgent;
        subscription.createdAt = Instant.now();
        return subscription;
    }

    /** The same phone signed in again, perhaps as someone else or in another household. */
    public void rebindPhone(UUID userId, UUID householdId) {
        this.userId = userId;
        this.householdId = householdId;
        this.failures = 0;
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
