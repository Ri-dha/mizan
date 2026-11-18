package iq.mizan.market.dto;

import java.time.Instant;

/** {@code previousMicros} is null for the first price ever recorded. */
public record PriceChangeResponse(long id, Instant observedAt, long priceMicros, Long previousMicros, String source) {
}
