package iq.mizan.household.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingInvitationSender implements InvitationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingInvitationSender.class);

    @Override
    public void send(String contact, String householdName, String token) {
        log.info("Invitation to household '{}' for {} issued (token delivered to the owner)", householdName, contact);
    }
}
