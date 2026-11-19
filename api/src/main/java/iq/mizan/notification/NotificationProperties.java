package iq.mizan.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Devices decide what is due and when (ADR e2e-1); the server only delivers. {@code dispatchInterval}
 * is how often due reminders go out, {@code staleAfter} how late a reminder may still be sent, and
 * {@code metalCron} when public price moves are checked.
 */
@ConfigurationProperties(prefix = "mizan.notifications")
public record NotificationProperties(boolean enabled, String transport, Duration dispatchInterval, Duration staleAfter,
                                     String metalCron, String timeZone, Duration ttl, Vapid vapid, Expo expo) {

    public record Vapid(String subject, String publicKey, String privateKey) {
    }

    /** ADR push-1: Expo's push service relays to APNs and FCM; the access token is optional. */
    public record Expo(String url, String accessToken) {
    }
}
