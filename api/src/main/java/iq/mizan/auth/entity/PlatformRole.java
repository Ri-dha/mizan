package iq.mizan.auth.entity;

/** Running the service, not any household (ADR admin-1); an operator still cannot read records. */
public enum PlatformRole {
    NONE,
    OPERATOR
}
