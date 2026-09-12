package iq.mizan.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.auth")
public record AuthProperties(
        String defaultCountryCode,
        Lockout lockout,
        Verification verification,
        PasswordReset passwordReset,
        RateLimit rateLimit) {

    public record Lockout(int maxAttempts, Duration duration) {
    }

    public record Verification(int codeLength, Duration ttl, int maxAttempts, Duration resendCooldown) {
    }

    public record PasswordReset(Duration ttl) {
    }

    public record RateLimit(int capacity, Duration refillPeriod) {
    }
}
