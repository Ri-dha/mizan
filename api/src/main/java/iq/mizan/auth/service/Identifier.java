package iq.mizan.auth.service;

import java.util.Locale;
import java.util.regex.Pattern;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

/** An account's contact address: an email or an E.164 phone number (FR-ACC-01). */
public record Identifier(Channel channel, String value) {

    public enum Channel {
        EMAIL,
        PHONE
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern LOCAL_PHONE = Pattern.compile("^0\\d{8,10}$");
    private static final Pattern PHONE_NOISE = Pattern.compile("[\\s\\-()]");

    public static Identifier email(String email) {
        return new Identifier(Channel.EMAIL, email);
    }

    public static Identifier phone(String phone) {
        return new Identifier(Channel.PHONE, phone);
    }

    public static Identifier parse(String raw, String defaultCountryCode) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.contains("@")) {
            if (!EMAIL.matcher(trimmed).matches()) {
                throw invalid();
            }
            return email(trimmed.toLowerCase(Locale.ROOT));
        }

        String digits = PHONE_NOISE.matcher(trimmed).replaceAll("");
        if (E164.matcher(digits).matches()) {
            return phone(digits);
        }
        if (LOCAL_PHONE.matcher(digits).matches()) {
            return phone(defaultCountryCode + digits.substring(1));
        }
        throw invalid();
    }

    public String emailOrNull() {
        return channel == Channel.EMAIL ? value : null;
    }

    public String phoneOrNull() {
        return channel == Channel.PHONE ? value : null;
    }

    private static ApiException invalid() {
        return ApiException.of(ErrorCode.INVALID_IDENTIFIER,
                "Enter a valid email address or phone number");
    }
}
