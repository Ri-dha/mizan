package iq.mizan.market.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceHistory;
import iq.mizan.market.entity.PriceQuote;
import iq.mizan.market.entity.PriceTick;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.repository.PriceHistoryRepository;
import iq.mizan.market.repository.PriceQuoteRepository;
import iq.mizan.market.repository.PriceTickRepository;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one fetched price: the current quote, the day's open/high/low/close and a tick when it
 * changed. Each of those reads before it writes, so two refreshes at once (a user's and the
 * poller's) would both insert the first row; a per-instrument lock makes them take turns.
 */
@Component
@AllArgsConstructor
class PriceRecorder {

    /** First half of the advisory lock key, so these locks cannot meet another feature's. */
    private static final int LOCK_NAMESPACE = 0x50524943;

    private final PriceQuoteRepository quotes;
    private final PriceHistoryRepository history;
    private final PriceTickRepository ticks;
    private final JdbcClient jdbc;
    private final MarketCache cache;
    private final Clock clock;

    /** Its own short transaction, so the lock is not held across the other instruments' network calls. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(Instrument instrument, String source, FetchedPrice price) {
        jdbc.sql("select pg_advisory_xact_lock(?, ?)").param(LOCK_NAMESPACE).param(instrument.ordinal()).query().singleRow();
        Instant now = clock.instant();
        quotes.findById(instrument).ifPresentOrElse(
                quote -> quote.update(price.priceMicros(), source, price.quotedAt(), now),
                () -> quotes.save(PriceQuote.of(instrument, price.priceMicros(), source, price.quotedAt(), now)));

        recordTickIfChanged(instrument, source, price);

        LocalDate day = price.quotedAt().atZone(ZoneOffset.UTC).toLocalDate();
        history.findById(new PriceHistory.Key(instrument, day)).ifPresentOrElse(
                point -> point.record(price.priceMicros(), source),
                () -> history.save(PriceHistory.of(instrument, day, price.priceMicros(), source)));
        cache.clearAfterCommit();
    }

    /** A tick per change, not per fetch: a feed answering the same price again adds nothing. */
    private void recordTickIfChanged(Instrument instrument, String source, FetchedPrice price) {
        boolean unchanged = ticks.findFirstByInstrumentOrderByObservedAtDesc(instrument)
                .map(last -> last.getPriceMicros() == price.priceMicros())
                .orElse(false);
        if (unchanged || ticks.existsByInstrumentAndObservedAt(instrument, price.quotedAt())) {
            return;
        }
        ticks.save(PriceTick.of(instrument, price.quotedAt(), price.priceMicros(), source));
    }
}
