package iq.mizan.notification.push;

import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.entity.PushSubscription;

import io.jsonwebtoken.Jwts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/** RFC 8030 delivery with RFC 8292 VAPID authentication. Stale endpoints report GONE so they can be dropped. */
@Component
@ConditionalOnProperty(name = "mizan.notifications.transport", havingValue = "webpush", matchIfMissing = true)
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final Duration VAPID_TOKEN_TTL = Duration.ofHours(12);
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final RestClient http;
    private final JsonMapper json;
    private final NotificationProperties properties;
    private final Clock clock;
    private final ECPrivateKey signingKey;
    private final String publicKey;

    public WebPushSender(RestClient.Builder builder, JsonMapper json, NotificationProperties properties, Clock clock) {
        this.http = builder.build();
        this.json = json;
        this.properties = properties;
        this.clock = clock;
        VapidKeys.Pair pair = configuredOrGenerated(properties.vapid());
        this.signingKey = VapidKeys.decodePrivate(pair.privateKey());
        this.publicKey = pair.publicKey();
    }

    private static VapidKeys.Pair configuredOrGenerated(NotificationProperties.Vapid vapid) {
        if (vapid.publicKey() != null && !vapid.publicKey().isBlank()) {
            return new VapidKeys.Pair(vapid.publicKey(), vapid.privateKey());
        }
        VapidKeys.Pair generated = VapidKeys.generate();
        log.warn("No VAPID keys configured; generated a pair for this run. Set MIZAN_VAPID_PUBLIC_KEY={} MIZAN_VAPID_PRIVATE_KEY=<private> to keep subscriptions across restarts",
                generated.publicKey());
        return generated;
    }

    public String publicKey() {
        return publicKey;
    }

    @Override
    public Outcome send(PushSubscription subscription, PushMessage message) {
        try {
            ECPublicKey subscriberKey = VapidKeys.decodePublic(subscription.getP256dh());
            byte[] body = WebPushCrypto.encrypt(json.writeValueAsBytes(message), subscriberKey, DECODER.decode(subscription.getAuth()));
            http.post().uri(subscription.getEndpoint())
                    .header("Authorization", "vapid t=" + vapidToken(subscription.getEndpoint()) + ", k=" + publicKey)
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", String.valueOf(properties.ttl().toSeconds()))
                    .header("Urgency", "normal")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .retrieve().toBodilessEntity();
            return Outcome.SENT;
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE) {
                return Outcome.GONE;
            }
            log.warn("Push to {} failed with {}", subscription.getEndpoint(), e.getStatusCode());
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            log.warn("Push to {} failed: {}", subscription.getEndpoint(), e.getMessage());
            return Outcome.FAILED;
        }
    }

    String vapidToken(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getAuthority();
        return Jwts.builder()
                .audience().add(audience).and()
                .subject(properties.vapid().subject())
                .expiration(Date.from(clock.instant().plus(VAPID_TOKEN_TTL)))
                .signWith(signingKey, Jwts.SIG.ES256)
                .compact();
    }
}
