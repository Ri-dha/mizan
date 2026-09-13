package iq.mizan.common.security;

import java.util.UUID;

/** Re-authentication for destructive actions; implemented where the password hash lives. */
public interface PasswordVerifier {

    boolean matches(UUID userId, String rawPassword);
}
