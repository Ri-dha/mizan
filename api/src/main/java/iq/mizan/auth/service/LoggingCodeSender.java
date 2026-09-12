package iq.mizan.auth.service;

import iq.mizan.auth.entity.CodePurpose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class LoggingCodeSender implements CodeSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingCodeSender.class);

    private volatile String lastCode;

    @Override
    public void send(Identifier contact, CodePurpose purpose, String code) {
        lastCode = code;
        log.info("[DEV ONLY] {} code for {} is {}", purpose, contact.value(), code);
    }

    public String lastCode() {
        return lastCode;
    }
}
