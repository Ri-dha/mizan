package iq.mizan.common.security;

/** Capabilities from BRD §4.2, carried as token claims and checked with hasAuthority. */
public enum Permission {
    HOUSEHOLD_VIEW,
    RECORD_WRITE,
    PLAN_EDIT,
    MEMBERS_MANAGE,
    DATA_EXPORT,
    HOUSEHOLD_DELETE,
    /** Aggregated reports over shared records; what an advisor gets and nothing more. */
    REPORTS_VIEW
}
