package iq.mizan.vault.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user's key material; every secret here was wrapped or encrypted on a device first. */
@Entity
@Table(name = "user_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserKey {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "wrapped_by_password", nullable = false)
    private String wrappedByPassword;

    @Column(name = "wrapped_by_recovery", nullable = false)
    private String wrappedByRecovery;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "encrypted_private_key", nullable = false)
    private String encryptedPrivateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserKey of(UUID userId, String wrappedByPassword, String wrappedByRecovery, String publicKey,
                             String encryptedPrivateKey, Instant now) {
        UserKey key = new UserKey();
        key.userId = userId;
        key.wrappedByPassword = wrappedByPassword;
        key.wrappedByRecovery = wrappedByRecovery;
        key.publicKey = publicKey;
        key.encryptedPrivateKey = encryptedPrivateKey;
        key.createdAt = now;
        key.updatedAt = now;
        return key;
    }

    /** After a password reset or change the device re-wraps the same user key; nothing else changes. */
    public void rewrapByPassword(String wrapped, Instant now) {
        this.wrappedByPassword = wrapped;
        this.updatedAt = now;
    }

    public void rewrapByRecovery(String wrapped, Instant now) {
        this.wrappedByRecovery = wrapped;
        this.updatedAt = now;
    }
}
