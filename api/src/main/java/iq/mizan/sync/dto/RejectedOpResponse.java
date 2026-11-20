package iq.mizan.sync.dto;

import java.util.UUID;

/** ADR sync-3: an op the server refused on its own merits; the device drops it and tells the person. */
public record RejectedOpResponse(UUID opId, String code, String detail) {
}
