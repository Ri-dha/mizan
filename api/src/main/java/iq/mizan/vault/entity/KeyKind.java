package iq.mizan.vault.entity;

/** Which shared key a grant carries (ADR e2e-1). */
public enum KeyKind {
    /** Encrypts every shared record of the household. */
    HOUSEHOLD,
    /** Encrypts only the annual reports, so advisors can read those without the household key. */
    REPORT
}
