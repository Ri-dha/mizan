package iq.mizan.auth.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import iq.mizan.auth.service.Identifier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    private UUID id;

    private String email;

    private String phone;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String locale;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppUser register(
            Identifier identifier, String displayName, String passwordHash, String locale) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.email = identifier.emailOrNull();
        user.phone = identifier.phoneOrNull();
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.locale = locale;
        user.accountStatus = AccountStatus.ACTIVE;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public Identifier contact() {
        return email != null ? Identifier.email(email) : Identifier.phone(phone);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** A pending deletion still signs in, so the person can export or change their mind (FR-ACC-08). */
    public boolean isActive() {
        return accountStatus != AccountStatus.CLOSED;
    }

    public void requestDeletion() {
        accountStatus = AccountStatus.DELETION_REQUESTED;
        deletionRequestedAt = Instant.now();
    }

    public void cancelDeletion() {
        accountStatus = AccountStatus.ACTIVE;
        deletionRequestedAt = null;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void markVerified() {
        if (verifiedAt == null) {
            verifiedAt = Instant.now();
        }
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        recordSuccessfulLogin();
    }

    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = Instant.now().plus(lockoutDuration);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
