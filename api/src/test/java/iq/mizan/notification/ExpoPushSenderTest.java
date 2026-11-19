package iq.mizan.notification;

import static org.assertj.core.api.Assertions.assertThat;

import iq.mizan.notification.push.ExpoPushSenderTickets;
import iq.mizan.notification.push.PushSender.Outcome;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** ADR push-1: Expo answers 200 either way, so the ticket decides whether a token is kept. */
class ExpoPushSenderTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void anAcceptedTicketIsSentAnUnregisteredDeviceIsGoneAndAnythingElseFailed() {
        assertThat(ExpoPushSenderTickets.outcomeOf(json.readTree("{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"}]}"))).isEqualTo(Outcome.SENT);
        assertThat(ExpoPushSenderTickets.outcomeOf(json.readTree("{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}"))).isEqualTo(Outcome.GONE);
        assertThat(ExpoPushSenderTickets.outcomeOf(json.readTree("{\"data\":[{\"status\":\"error\",\"details\":{\"error\":\"MessageRateExceeded\"}}]}"))).isEqualTo(Outcome.FAILED);
        assertThat(ExpoPushSenderTickets.outcomeOf(json.readTree("{\"errors\":[{\"code\":\"VALIDATION_ERROR\"}]}"))).isEqualTo(Outcome.FAILED);
    }
}
