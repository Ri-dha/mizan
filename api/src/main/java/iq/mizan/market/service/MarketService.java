package iq.mizan.market.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import iq.mizan.market.MarketSettings;
import iq.mizan.market.dto.CatalogueResponse;
import iq.mizan.market.repository.MarketCatalogueRepository;
import tools.jackson.databind.json.JsonMapper;
import iq.mizan.market.dto.QuoteResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.feed.PriceFeed;
import iq.mizan.market.mapper.MarketMapper;
import iq.mizan.market.repository.PriceQuoteRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
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
    private final PriceRecorder recorder;
    private final MarketCatalogueRepository catalogues;
    private final JsonMapper json;
    private final MarketMapper mapper;
    private final MarketSettings settings;
    private final Clock clock;
    private final AtomicReference<Instant> lastManualRefresh = new AtomicReference<>(Instant.EPOCH);
    private final Map<String, FeedHealth> health = new ConcurrentHashMap<>();

    /** Since this instance started; a feed that never answered or failed is not listed. */
    public record FeedHealth(Instant lastSuccessAt, Instant lastFailureAt, String lastError) {
        static FeedHealth succeeded(FeedHealth previous, Instant at) {
            return previous == null ? new FeedHealth(at, null, null) : new FeedHealth(at, previous.lastFailureAt(), previous.lastError());
        }

        static FeedHealth failed(FeedHealth previous, Instant at, String error) {
            return new FeedHealth(previous == null ? null : previous.lastSuccessAt(), at, error);
        }
    }

    public Map<String, FeedHealth> feedHealth() {
        return Map.copyOf(health);
    }

    public List<String> feedNames() {
        return feeds.stream().map(PriceFeed::name).toList();
    }

    /** An operator's correction: recorded like any price, from source "correction". */
    @Transactional
    public void correct(Instrument instrument, long priceMicros) {
        recorder.store(instrument, CORRECTION_SOURCE, new FetchedPrice(priceMicros, clock.instant()));
    }

    public static final String CORRECTION_SOURCE = "correction";

    @Transactional
    public void refresh() {
        refresh(EnumSet.allOf(Instrument.class));
    }

    @Transactional
    public void refresh(Set<Instrument> instruments) {
        for (Instrument instrument : instruments) {
            fetchFromFirstAnsweringFeed(instrument).ifPresent(fetched -> recorder.store(instrument, fetched.feed().name(), fetched.price()));
        }
    }

    /** A user asking for fresh prices gets them, but not more often than the cooldown, whoever asks. */
    public List<QuoteResponse> refreshOnRequest() {
        Instant now = clock.instant();
        Instant last = lastManualRefresh.get();
        if (last.plus(settings.refreshCooldown()).isBefore(now) && lastManualRefresh.compareAndSet(last, now)) {
            refresh();
        }
        return quotes();
    }

    @Cacheable(MarketCache.QUOTES)
    @Transactional(readOnly = true)
    public List<QuoteResponse> quotes() {
        Instant now = clock.instant();
        return quotes.findAll().stream()
                .map(quote -> mapper.toResponse(quote, quote.isStaleAt(now, settings.staleAfter())))
                .toList();
    }

    /** The dealer's product list as last fetched, or empty when no dealer feed has answered yet. */
    @Transactional(readOnly = true)
    public Optional<CatalogueResponse> catalogue() {
        return catalogues.findAll().stream().findFirst()
                .map(c -> new CatalogueResponse(c.getSource(), c.getFetchedAt(), c.getAsOf(), json.readTree(c.getPayload())));
    }

    private record Answer(PriceFeed feed, FetchedPrice price) {
    }

    private Optional<Answer> fetchFromFirstAnsweringFeed(Instrument instrument) {
        for (PriceFeed feed : GoldFeedOrder.arrange(instrument, feeds, settings.goldPrimary())) {
            if (!feed.supports(instrument)) {
                continue;
            }
            try {
                Optional<FetchedPrice> price = feed.fetch(instrument);
                if (price.isPresent()) {
                    health.compute(feed.name(), (name, h) -> FeedHealth.succeeded(h, clock.instant()));
                    return Optional.of(new Answer(feed, price.get()));
                }
            } catch (RuntimeException e) {
                health.compute(feed.name(), (name, h) -> FeedHealth.failed(h, clock.instant(), instrument + ": " + e.getMessage()));
                log.warn("Price feed {} failed for {}: {}", feed.name(), instrument, e.getMessage());
            }
        }
        return Optional.empty();
    }
}
