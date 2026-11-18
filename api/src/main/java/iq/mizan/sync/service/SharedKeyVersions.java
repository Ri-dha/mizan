package iq.mizan.sync.service;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * The current version of a household's shared key, by its sealed-field letter (`h` or `r`).
 * Implemented by the vault module, which owns the keys; sync only refuses stale seals with it.
 */
public interface SharedKeyVersions {

    OptionalInt current(UUID householdId, char sealKind);
}
