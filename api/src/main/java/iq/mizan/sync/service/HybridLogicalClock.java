package iq.mizan.sync.service;

import java.util.regex.Pattern;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

/**
 * Timestamps of the form {@code <13-digit wall ms>:<4-hex counter>:<node id>}, ordered by
 * plain string comparison. Wall clock first so a stamp reads as a time, the counter to order
 * writes within one millisecond, the node id to break ties between devices deterministically.
 */
public final class HybridLogicalClock {

    private static final Pattern FORMAT = Pattern.compile("^\\d{13}:[0-9a-f]{4}:[A-Za-z0-9-]{1,36}$");

    private HybridLogicalClock() {
    }

    public static boolean isAfter(String candidate, String reference) {
        return reference == null || candidate.compareTo(reference) > 0;
    }

    public static String validated(String clock, String field) {
        if (clock == null || !FORMAT.matcher(clock).matches()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "Field '%s' has no valid clock".formatted(field));
        }
        return clock;
    }
}
