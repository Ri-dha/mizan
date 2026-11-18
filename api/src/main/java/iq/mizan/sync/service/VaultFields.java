package iq.mizan.sync.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The wire contract for encrypted records (ADR e2e-1): every field is ciphertext except the two
 * the server itself needs, visibility for row-level security and deletedAt for the purge.
 */
public final class VaultFields {

    public static final String VISIBILITY = "visibility";
    public static final String DELETED_AT = "deletedAt";
    public static final Set<String> PLAIN = Set.of(VISIBILITY, DELETED_AT);
    public static final Set<String> VISIBILITIES = Set.of("SHARED", "PRIVATE");
    public static final String SHARED = "SHARED";

    /** e1 = format version; h, u or r = household, user or report key; then key version and the IV plus ciphertext. */
    private static final Pattern CIPHERTEXT = Pattern.compile("^e1\\.[hur]\\.[0-9]{1,4}\\.[A-Za-z0-9_-]{24,262144}$");

    private VaultFields() {
    }

    public static boolean isCiphertext(Object value) {
        return value instanceof String text && CIPHERTEXT.matcher(text).matches();
    }
}
