package iq.mizan.auth.entity;

/** What the stored hash protects (ADR e2e-1). LEGACY accounts move to KEY at their next sign-in. */
public enum AuthScheme {
    /** A hash of the password itself, as accounts were created before end-to-end encryption. */
    LEGACY,
    /** A hash of the auth key the device derives from the password; the server never sees the password. */
    KEY
}
