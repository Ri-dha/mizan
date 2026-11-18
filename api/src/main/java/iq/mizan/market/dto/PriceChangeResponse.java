package iq.mizan.market.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** {@code previousMicros} is null for the first price ever recorded. */
public record PriceChangeResponse(long id, Instant observedAt, long priceMicros, @Nullable Long previousMicros, String source) {
}
