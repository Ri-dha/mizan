package iq.mizan.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.notifications")
public record NotificationProperties(boolean enabled, String transport, String dailyCron, String timeZone, Duration ttl,
                                     Vapid vapid, Defaults defaults) {

    public record Vapid(String subject, String publicKey, String privateKey) {
    }

    public record Defaults(int billLeadDays, int overspendThresholdBasisPoints, int metalMoveBasisPoints) {
    }
}
