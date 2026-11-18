package iq.mizan.household.service;

import java.util.Set;
import java.util.UUID;

import iq.mizan.common.security.Permission;

/** An active member as other modules may see one: identity, role name and what the role permits. */
public record MemberAccess(UUID userId, String displayName, String role, Set<Permission> permissions) {
}
