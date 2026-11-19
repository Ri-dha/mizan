package iq.mizan.notification.push;

import tools.jackson.databind.JsonNode;

/** Reads Expo's push reply: a 200 carries a ticket per message, and the ticket says whether it was accepted. */
public final class ExpoPushSenderTickets {

    private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private ExpoPushSenderTickets() {
    }

    public static PushSender.Outcome outcomeOf(JsonNode reply) {
        JsonNode ticket = reply == null ? null : reply.path("data").path(0);
        if (ticket == null || ticket.isMissingNode()) {
            return PushSender.Outcome.FAILED;
        }
        if ("ok".equals(ticket.path("status").asString())) {
            return PushSender.Outcome.SENT;
        }
        return DEVICE_NOT_REGISTERED.equals(ticket.path("details").path("error").asString()) ? PushSender.Outcome.GONE : PushSender.Outcome.FAILED;
    }
}
