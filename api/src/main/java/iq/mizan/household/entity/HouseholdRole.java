package iq.mizan.household.entity;

import java.util.EnumSet;
import java.util.Set;

import iq.mizan.common.security.Permission;

/** BRD §4.2. A new role is a new constant; no call site changes. */
public enum HouseholdRole {

    OWNER(EnumSet.allOf(Permission.class)),
    MEMBER(EnumSet.of(Permission.HOUSEHOLD_VIEW, Permission.RECORD_WRITE, Permission.PLAN_EDIT,
            Permission.DATA_EXPORT, Permission.REPORTS_VIEW)),
    VIEWER(EnumSet.of(Permission.HOUSEHOLD_VIEW, Permission.REPORTS_VIEW)),
    /** Own records only: without HOUSEHOLD_VIEW the session is bound restricted and shared rows stay hidden. */
    DEPENDENT(EnumSet.of(Permission.RECORD_WRITE)),
    /** Temporary, read-only access to aggregated reports; never to raw records. */
    ADVISOR(EnumSet.of(Permission.REPORTS_VIEW));

    private final Set<Permission> permissions;

    HouseholdRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
