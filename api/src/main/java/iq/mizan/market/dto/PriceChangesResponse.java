package iq.mizan.market.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/** Newest first; pass {@code nextBefore} as {@code before} for the next page, null when there is none. */
public record PriceChangesResponse(List<PriceChangeResponse> changes, @Nullable Instant nextBefore) {
}
