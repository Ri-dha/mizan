package iq.mizan.notification.push;

import java.util.List;
import java.util.Map;

import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.entity.PushChannel;
import iq.mizan.notification.entity.PushSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * ADR push-1: phones are reached through Expo's push service, which relays to APNs and FCM. The
 * message is the same fixed wording Web Push carries; a token Expo reports unregistered is GONE.
 */
@Component
@ConditionalOnProperty(name = "mizan.notifications.transport", havingValue = "webpush", matchIfMissing = true)
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

    private final RestClient http;
    private final NotificationProperties properties;

    public ExpoPushSender(RestClient.Builder builder, NotificationProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    @Override
    public boolean handles(PushChannel channel) {
        return channel == PushChannel.EXPO;
    }

    @Override
    public Outcome send(PushSubscription subscription, PushMessage message) {
        try {
            JsonNode reply = http.post().uri(properties.expo().url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        String token = properties.expo().accessToken();
                        if (token != null && !token.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .body(List.of(Map.of(
                            "to", subscription.getEndpoint(),
                            "title", message.title(),
                            "body", message.body(),
                            "sound", "default",
                            "data", Map.of("kind", message.kind(), "url", message.url()))))
                    .retrieve().body(JsonNode.class);
            return ExpoPushSenderTickets.outcomeOf(reply);
        } catch (RestClientException e) {
            log.warn("Expo push failed: {}", e.getMessage());
            return Outcome.FAILED;
        }
    }
}
