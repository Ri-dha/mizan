package iq.mizan.market.feed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * IQWealth (iraqsm.com) {@code GET /fx}: the Baghdad market dollar and the Central Bank rate.
 * The free key allows 100 requests a day for everyone, so the network is touched only by
 * {@link #poll()} on a schedule; {@link #fetch} answers from the last poll, which keeps a user's
 * Refresh from spending the quota.
 */
@Component
@Order(2)
public class IqWealthFxFeed implements PriceFeed {

    public static final String NAME = "iraqsm.com";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    public enum Outcome { UPDATED, UNCHANGED, NO_KEY, BUDGET_KEPT, PAUSED_UNTIL_MIDNIGHT, KEY_REFUSED }

    public enum Side { BUY, SELL, MID }

    record Rates(Long parallelMicros, Instant parallelAt, Long officialMicros, Instant officialAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(IqWealthFxFeed.class);

    private final RestClient client;
    private final MarketProperties.IqWealth config;
    private final MarketSettings settings;
    private final ZoneId zone;
    private final Clock clock;
    private volatile Rates rates;
    private LocalDate budgetDay;
    private int remaining = Integer.MAX_VALUE;
    private LocalDate pausedOn;
    private String refusedKey;
    private volatile Instant lastPollAt;
    private volatile Outcome lastOutcome;

    public IqWealthFxFeed(RestClient.Builder builder, MarketProperties properties, MarketSettings settings, Clock clock) {
        this.config = properties.feeds().iqWealth();
        this.settings = settings;
        this.client = builder.build();
        this.zone = ZoneId.of(config.zone());
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return (instrument == Instrument.USDIQD_PARALLEL || instrument == Instrument.USDIQD_OFFICIAL) && rates != null;
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Rates known = rates;
        if (known == null) {
            return Optional.empty();
        }
        return instrument == Instrument.USDIQD_PARALLEL
                ? price(known.parallelMicros(), known.parallelAt())
                : price(known.officialMicros(), known.officialAt());
    }

    public boolean hasRates() {
        return rates != null;
    }

    /** One request, unless the key is missing or refused, today's quota is down to the reserve, or the provider said stop. */
    public synchronized Outcome poll() {
        String apiKey = settings.iqWealthApiKey();
        if (!Configured.present(apiKey)) {
            return Outcome.NO_KEY;
        }
        if (apiKey.equals(refusedKey)) {
            return Outcome.KEY_REFUSED;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (today.equals(pausedOn)) {
            return Outcome.PAUSED_UNTIL_MIDNIGHT;
        }
        if (today.equals(budgetDay) && remaining <= settings.iqWealthReserve()) {
            return Outcome.BUDGET_KEPT;
        }
        try {
            ResponseEntity<Map> response = client.get().uri(config.url() + "/fx")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toEntity(Map.class);
            countRemaining(today, response.getHeaders().getFirst(REMAINING_HEADER));
            Rates next = parse(response.getBody(), Side.valueOf(settings.iqWealthParallelSide()));
            boolean changed = !next.equals(rates);
            rates = next;
            return polled(changed ? Outcome.UPDATED : Outcome.UNCHANGED);
        } catch (HttpClientErrorException e) {
            return polled(refused(e, today, apiKey));
        }
    }

    private Outcome polled(Outcome outcome) {
        lastPollAt = clock.instant();
        lastOutcome = outcome;
        return outcome;
    }

    /** For the operator dashboard: today's quota as the provider last reported it. */
    public synchronized Quota quota() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Integer left = today.equals(budgetDay) && remaining != Integer.MAX_VALUE ? remaining : null;
        return new Quota(Configured.present(settings.iqWealthApiKey()), left, settings.iqWealthReserve(),
                today.equals(pausedOn), settings.iqWealthApiKey().equals(refusedKey), lastPollAt, lastOutcome);
    }

    public record Quota(boolean configured, Integer remainingToday, int reserve, boolean pausedUntilMidnight,
                        boolean keyRefused, Instant lastPollAt, Outcome lastOutcome) {
    }

    private Outcome refused(HttpClientErrorException e, LocalDate today, String apiKey) {
        if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            pausedOn = today;
            log.warn("IQWealth daily limit reached; polling resumes after midnight {}", zone);
            return Outcome.PAUSED_UNTIL_MIDNIGHT;
        }
        if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED) || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
            refusedKey = apiKey;
            log.error("IQWealth refused the key ({}); the feed is off until the key is replaced", e.getStatusCode());
            return Outcome.KEY_REFUSED;
        }
        throw e;
    }

    private void countRemaining(LocalDate today, String header) {
        budgetDay = today;
        if (header == null) {
            return;
        }
        try {
            remaining = Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            log.warn("IQWealth sent an unreadable {} header", REMAINING_HEADER);
        }
    }

    /** {"data": {"parallel": {"buy", "sell", "stale", "publishedAt"}, "official": {"cbi"}}, "generatedAt"}; a stale parallel rate is left out. */
    static Rates parse(Map<?, ?> body, Side side) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) {
            throw new IllegalStateException("IQWealth /fx answered without data");
        }
        Instant generatedAt = body.get("generatedAt") instanceof String text ? Instant.parse(text) : Instant.now();
        Long parallel = null;
        Instant parallelAt = null;
        if (data.get("parallel") instanceof Map<?, ?> market && !Boolean.TRUE.equals(market.get("stale"))) {
            parallel = sideOf(market, side);
            parallelAt = market.get("publishedAt") instanceof String text ? Instant.parse(text) : generatedAt;
        }
        Long official = data.get("official") instanceof Map<?, ?> cbi && cbi.get("cbi") != null ? Micros.of(cbi.get("cbi")) : null;
        return new Rates(parallel, parallelAt, official, generatedAt);
    }

    private static Long sideOf(Map<?, ?> market, Side side) {
        Object buy = market.get("buy");
        Object sell = market.get("sell");
        if (buy == null || sell == null) {
            return null;
        }
        return switch (side) {
            case BUY -> Micros.of(buy);
            case SELL -> Micros.of(sell);
            case MID -> Math.round((Micros.of(buy) + Micros.of(sell)) / 2.0);
        };
    }

    private static Optional<FetchedPrice> price(Long micros, Instant at) {
        return micros == null ? Optional.empty() : Optional.of(new FetchedPrice(micros, at));
    }
}
