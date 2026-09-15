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
import iq.mizan.market.dto.CatalogueResponse;
import iq.mizan.market.dto.HistoryPointResponse;
import iq.mizan.market.repository.MarketCatalogueRepository;
import tools.jackson.databind.json.JsonMapper;
import iq.mizan.market.dto.QuoteResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceHistory;
import iq.mizan.market.entity.PriceQuote;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.feed.PriceFeed;
import iq.mizan.market.mapper.MarketMapper;
import iq.mizan.market.repository.PriceHistoryRepository;
import iq.mizan.market.repository.PriceQuoteRepository;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final List<PriceFeed> feeds;
    private final PriceQuoteRepository quotes;
    private final PriceHistoryRepository history;
    private final MarketCatalogueRepository catalogues;
    private final JsonMapper json;
    private final MarketMapper mapper;
    private final MarketProperties properties;
    private final Clock clock;
    private volatile Instant lastManualRefresh = Instant.EPOCH;

    @Transactional
    public void refresh() {
        for (Instrument instrument : Instrument.values()) {
            fetchFromFirstAnsweringFeed(instrument).ifPresent(fetched -> store(instrument, fetched.feed(), fetched.price()));
        }
    }

    /** A user asking for fresh prices gets them, but not more often than the cooldown, whoever asks. */
    public List<QuoteResponse> refreshOnRequest() {
        Instant now = clock.instant();
        if (lastManualRefresh.plus(properties.manualRefreshCooldown()).isBefore(now)) {
            lastManualRefresh = now;
            refresh();
        }
        return quotes();
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> quotes() {
        Instant now = clock.instant();
        return quotes.findAll().stream()
                .map(quote -> mapper.toResponse(quote, quote.isStaleAt(now, properties.staleAfter())))
                .toList();
    }

    /** The dealer's product list as last fetched, or empty when no dealer feed has answered yet. */
    @Transactional(readOnly = true)
    public Optional<CatalogueResponse> catalogue() {
        return catalogues.findAll().stream().findFirst()
                .map(c -> new CatalogueResponse(c.getSource(), c.getFetchedAt(), c.getAsOf(), json.readTree(c.getPayload())));
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
