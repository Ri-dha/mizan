package iq.mizan.market.dto;

import java.time.Instant;

import iq.mizan.market.entity.Instrument;

/** BR-10: every price carries its source and time, and says when it is stale. */
public record QuoteResponse(Instrument instrument, long priceMicros, String source, Instant quotedAt, Instant fetchedAt, boolean stale) {
}
