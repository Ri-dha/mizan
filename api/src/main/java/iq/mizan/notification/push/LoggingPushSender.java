package iq.mizan.notification.push;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import iq.mizan.notification.entity.PushSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mizan.notifications.transport", havingValue = "log")
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    public record Delivery(PushSubscription subscription, PushMessage message) {
    }

    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

    @Override
    public Outcome send(PushSubscription subscription, PushMessage message) {
        deliveries.add(new Delivery(subscription, message));
        log.info("[DEV ONLY] push to {}: {} — {}", subscription.getEndpoint(), message.title(), message.body());
        return Outcome.SENT;
    }

    public List<Delivery> deliveries() {
        return List.copyOf(deliveries);
    }

    public void clear() {
        deliveries.clear();
    }
}
