package iq.mizan.notification.push;

import iq.mizan.notification.entity.PushChannel;
import iq.mizan.notification.entity.PushSubscription;

/** A transport behind notifications: Web Push and Expo in production, a recorder in tests. */
public interface PushSender {

    boolean handles(PushChannel channel);

    enum Outcome {
        SENT,
        FAILED,
        GONE
    }

    Outcome send(PushSubscription subscription, PushMessage message);
}
