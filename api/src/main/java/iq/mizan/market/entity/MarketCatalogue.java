package iq.mizan.market.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A dealer's product list as last fetched; the payload is the provider's JSON, shown as is. */
@Entity
@Table(name = "market_catalogue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCatalogue {

    @Id
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(nullable = false)
    private String payload;

    public static MarketCatalogue of(String source, Instant fetchedAt, Instant asOf, String payload) {
        MarketCatalogue catalogue = new MarketCatalogue();
        catalogue.source = source;
        catalogue.record(fetchedAt, asOf, payload);
        return catalogue;
    }

    public void record(Instant fetchedAt, Instant asOf, String payload) {
        this.fetchedAt = fetchedAt;
        this.asOf = asOf;
        this.payload = payload;
    }
}
