package iq.mizan.household.entity;

import java.util.EnumSet;
import java.util.Set;

import iq.mizan.common.security.Permission;

/** BRD §4.2. A new role is a new constant; no call site changes. */
public enum HouseholdRole {

    OWNER(EnumSet.allOf(Permission.class)),
    MEMBER(EnumSet.of(Permission.HOUSEHOLD_VIEW, Permission.RECORD_WRITE, Permission.PLAN_EDIT,
            Permission.DATA_EXPORT)),
    VIEWER(EnumSet.of(Permission.HOUSEHOLD_VIEW)),
    DEPENDENT(EnumSet.of(Permission.RECORD_WRITE));

    private final Set<Permission> permissions;

    HouseholdRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
