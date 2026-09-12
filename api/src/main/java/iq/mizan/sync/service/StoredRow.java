package iq.mizan.sync.service;

import java.util.Map;
import java.util.UUID;

/** One syncable row as the database holds it, values normalised for comparison. */
public record StoredRow(UUID id, UUID ownerId, Map<String, Object> fields, Map<String, String> clocks) {
}
