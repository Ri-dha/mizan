package iq.mizan.common.security;

import java.util.Set;
import java.util.UUID;

/** The authenticated principal: who is calling and which household they act in. */
public record CurrentUser(UUID userId, UUID householdId, Set<Permission> permissions) {
}
