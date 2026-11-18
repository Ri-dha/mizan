package iq.mizan.market.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One change of a price, kept forever; written only when the price differs from the last one. */
@Entity
@Table(name = "price_tick")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Instrument instrument;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "price_micros", nullable = false)
    private long priceMicros;

    @Column(nullable = false)
    private String source;

    public static PriceTick of(Instrument instrument, Instant observedAt, long priceMicros, String source) {
        PriceTick tick = new PriceTick();
        tick.instrument = instrument;
        tick.observedAt = observedAt;
        tick.priceMicros = priceMicros;
        tick.source = source;
        return tick;
    }
}
