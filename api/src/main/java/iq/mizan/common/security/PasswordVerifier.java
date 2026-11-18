package iq.mizan.common.security;

import java.util.UUID;

/** Re-authentication for destructive actions with the device-derived auth key; implemented where the hash lives. */
public interface PasswordVerifier {

    boolean matches(UUID userId, String authKey);
}
