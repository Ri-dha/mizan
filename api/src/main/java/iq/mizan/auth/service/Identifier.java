package iq.mizan.auth.service;

import java.util.Locale;
import java.util.regex.Pattern;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

/**
 * An account's sign-in address (ADR auth-email-1). The trim and lower-casing must stay in step with
 * {@code normaliseIdentifier} in packages/core, because the client derives the key salt from it.
 */
public record Identifier(String email) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static Identifier parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!EMAIL.matcher(trimmed).matches()) {
            throw ApiException.of(ErrorCode.INVALID_IDENTIFIER, "Enter a valid email address");
        }
        return new Identifier(trimmed.toLowerCase(Locale.ROOT));
    }
}
