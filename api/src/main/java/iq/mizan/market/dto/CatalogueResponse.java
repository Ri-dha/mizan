package iq.mizan.market.dto;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** The dealer's own list: groups of bars and coins with the price they pay (bid) and charge (ask). */
public record CatalogueResponse(String source, Instant fetchedAt, Instant asOf, JsonNode catalogue) {
}
