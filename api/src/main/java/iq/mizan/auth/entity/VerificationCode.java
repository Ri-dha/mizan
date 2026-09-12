package iq.mizan.auth.entity;

import java.time.Duration;
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

@Entity
@Table(name = "verification_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodePurpose purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static VerificationCode issue(UUID userId, CodePurpose purpose, String codeHash, Duration ttl) {
        VerificationCode code = new VerificationCode();
        code.id = UUID.randomUUID();
        code.userId = userId;
        code.purpose = purpose;
        code.codeHash = codeHash;
        code.createdAt = Instant.now();
        code.expiresAt = code.createdAt.plus(ttl);
        return code;
    }

    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }

    public boolean isIssuedWithin(Duration window) {
        return createdAt.plus(window).isAfter(Instant.now());
    }

    public boolean hasAttemptsLeft(int maxAttempts) {
        return attempts < maxAttempts;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public void consume() {
        consumedAt = Instant.now();
    }
}
