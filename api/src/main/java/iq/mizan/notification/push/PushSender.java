package iq.mizan.notification.push;

import iq.mizan.notification.entity.PushSubscription;

/** The transport behind notifications; Web Push in production, a recorder in tests. */
public interface PushSender {

    enum Outcome {
        SENT,
        FAILED,
        GONE
    }

    Outcome send(PushSubscription subscription, PushMessage message);
}
