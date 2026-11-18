package iq.mizan.market.dto;

import java.time.Instant;
import java.util.List;

/** Newest first; pass {@code nextBefore} as {@code before} for the next page, null when there is none. */
public record PriceChangesResponse(List<PriceChangeResponse> changes, Instant nextBefore) {
}
