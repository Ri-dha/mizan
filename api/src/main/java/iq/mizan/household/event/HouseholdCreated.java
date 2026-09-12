package iq.mizan.household.event;

import java.util.UUID;

/** Published inside the creating transaction; listeners that seed data commit with it. */
public record HouseholdCreated(UUID householdId, UUID ownerId, int monthStartDay) {
}
