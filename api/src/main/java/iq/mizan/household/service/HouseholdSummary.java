package iq.mizan.household.service;

import java.util.Set;
import java.util.UUID;

import iq.mizan.common.security.Permission;
import iq.mizan.household.entity.HouseholdRole;

/** What other modules need to know about a user's household, without touching its entities. */
public record HouseholdSummary(
        UUID id,
        String name,
        String baseCurrency,
        int monthStartDay,
        HouseholdRole role,
        Set<Permission> permissions) {
}
