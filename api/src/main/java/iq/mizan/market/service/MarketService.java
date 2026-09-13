package iq.mizan.market.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.market.MarketProperties;
import iq.mizan.market.dto.HistoryPointResponse;
import iq.mizan.market.dto.QuoteResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceHistory;
import iq.mizan.market.entity.PriceQuote;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.feed.PriceFeed;
import iq.mizan.market.mapper.MarketMapper;
import iq.mizan.market.repository.PriceHistoryRepository;
import iq.mizan.market.repository.PriceQuoteRepository;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches once for everyone and serves from the table (FR-MKT-01, NFR-10). A provider that
 * fails is skipped for the next one; if all fail the last quote stays, marked stale after
 * the configured age, and is never blanked (FR-MKT-05).
 */
@Service
@AllArgsConstructor
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final List<PriceFeed> feeds;
    private final PriceQuoteRepository quotes;
    private final PriceHistoryRepository history;
    private final MarketMapper mapper;
    private final MarketProperties properties;
    private final Clock clock;

    @Transactional
    public void refresh() {
        for (Instrument instrument : Instrument.values()) {
            fetchFromFirstAnsweringFeed(instrument).ifPresent(fetched -> store(instrument, fetched.feed(), fetched.price()));
        }
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> quotes() {
        Instant now = clock.instant();
        return quotes.findAll().stream()
                .map(quote -> mapper.toResponse(quote, quote.isStaleAt(now, properties.staleAfter())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryPointResponse> history(Instrument instrument, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(properties.historyDefaultDays()) : from;
        if (start.plusDays(properties.historyMaxDays()).isBefore(end)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "History is limited to %d days per request".formatted(properties.historyMaxDays()));
        }
        return history.findByKeyInstrumentAndKeyDayBetweenOrderByKeyDay(instrument, start, end).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private record Answer(PriceFeed feed, FetchedPrice price) {
    }

    private Optional<Answer> fetchFromFirstAnsweringFeed(Instrument instrument) {
        for (PriceFeed feed : feeds) {
            if (!feed.supports(instrument)) {
                continue;
            }
            try {
                Optional<FetchedPrice> price = feed.fetch(instrument);
                if (price.isPresent()) {
                    return Optional.of(new Answer(feed, price.get()));
                }
            } catch (RuntimeException e) {
                log.warn("Price feed {} failed for {}: {}", feed.name(), instrument, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private void store(Instrument instrument, PriceFeed feed, FetchedPrice price) {
        Instant now = clock.instant();
        quotes.findById(instrument).ifPresentOrElse(
                quote -> quote.update(price.priceMicros(), feed.name(), price.quotedAt(), now),
                () -> quotes.save(PriceQuote.of(instrument, price.priceMicros(), feed.name(), price.quotedAt(), now)));

        LocalDate day = price.quotedAt().atZone(ZoneOffset.UTC).toLocalDate();
        history.findById(new PriceHistory.Key(instrument, day)).ifPresentOrElse(
                point -> point.record(price.priceMicros(), feed.name()),
                () -> history.save(PriceHistory.of(instrument, day, price.priceMicros(), feed.name())));
    }
}
