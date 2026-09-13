package iq.mizan.market.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_quote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceQuote {

    @Id
    @Enumerated(EnumType.STRING)
    private Instrument instrument;

    @Column(name = "price_micros", nullable = false)
    private long priceMicros;

    @Column(nullable = false)
    private String source;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public static PriceQuote of(Instrument instrument, long priceMicros, String source, Instant quotedAt, Instant fetchedAt) {
        PriceQuote quote = new PriceQuote();
        quote.instrument = instrument;
        quote.update(priceMicros, source, quotedAt, fetchedAt);
        return quote;
    }

    public void update(long priceMicros, String source, Instant quotedAt, Instant fetchedAt) {
        this.priceMicros = priceMicros;
        this.source = source;
        this.quotedAt = quotedAt;
        this.fetchedAt = fetchedAt;
    }

    public boolean isStaleAt(Instant now, java.time.Duration staleAfter) {
        return fetchedAt.plus(staleAfter).isBefore(now);
    }
}
