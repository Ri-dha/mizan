package iq.mizan.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** A real hash no password matches, compared against when the account does not exist. */
@Component
public class DummyPasswordHash {

    private final String value;

    public DummyPasswordHash(PasswordEncoder passwordEncoder) {
        this.value = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }
}
