package iq.mizan.market.feed;

import java.time.Instant;

public record FetchedPrice(long priceMicros, Instant quotedAt) {
}
