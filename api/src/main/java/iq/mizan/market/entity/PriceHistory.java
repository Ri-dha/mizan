package iq.mizan.market.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row per instrument per day (FR-MKT-04): the close is the last fetch, with the day's open, high and low. */
@Entity
@Table(name = "price_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceHistory {

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Instrument instrument;

        @Column(nullable = false)
        private LocalDate day;

        public Key(Instrument instrument, LocalDate day) {
            this.instrument = instrument;
            this.day = day;
        }
    }

    @EmbeddedId
    private Key key;

    @Column(name = "price_micros", nullable = false)
    private long priceMicros;

    @Column(name = "open_micros", nullable = false)
    private long openMicros;

    @Column(name = "high_micros", nullable = false)
    private long highMicros;

    @Column(name = "low_micros", nullable = false)
    private long lowMicros;

    @Column(nullable = false)
    private String source;

    public static PriceHistory of(Instrument instrument, LocalDate day, long priceMicros, String source) {
        PriceHistory history = new PriceHistory();
        history.key = new Key(instrument, day);
        history.openMicros = priceMicros;
        history.highMicros = priceMicros;
        history.lowMicros = priceMicros;
        history.record(priceMicros, source);
        return history;
    }

    public void record(long priceMicros, String source) {
        this.priceMicros = priceMicros;
        this.highMicros = Math.max(highMicros, priceMicros);
        this.lowMicros = Math.min(lowMicros, priceMicros);
        this.source = source;
    }
}
