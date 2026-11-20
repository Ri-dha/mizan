package iq.mizan.market.feed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.MarketSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The one way into IQWealth (iraqsm.com). Its free key allows 100 requests a day across every
 * endpoint, so the dollar and gold feeds share this client and the day's count: a request is
 * made only while the key is set and accepted, the provider has not said stop, and the day's
 * remaining requests are above the operator's reserve.
 */
@Component
public class IqWealthClient {

    public static final String NAME = "iraqsm.com";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    public enum Outcome { UPDATED, UNCHANGED, NO_KEY, BUDGET_KEPT, PAUSED_UNTIL_MIDNIGHT, KEY_REFUSED }

    /** Either the body of an answered request, or why no request was made. */
    public record Reply(Map<?, ?> body, Outcome skipped) {

        public boolean answered() {
            return skipped == null;
        }
    }

    public record Quota(boolean configured, Integer remainingToday, int reserve, boolean pausedUntilMidnight,
                        boolean keyRefused, Instant lastPollAt, Outcome lastOutcome) {
    }

    private static final Logger log = LoggerFactory.getLogger(IqWealthClient.class);

    private final RestClient client;
    private final MarketProperties.IqWealth config;
    private final MarketSettings settings;
    private final ZoneId zone;
    private final Clock clock;
    private LocalDate budgetDay;
    private int remaining = Integer.MAX_VALUE;
    private LocalDate pausedOn;
    private String refusedKey;
    private volatile Instant lastPollAt;
    private volatile Outcome lastOutcome;

    public IqWealthClient(RestClient.Builder builder, MarketProperties properties, MarketSettings settings, Clock clock) {
        this.config = properties.feeds().iqWealth();
        this.settings = settings;
        this.client = builder.build();
        this.zone = ZoneId.of(config.zone());
        this.clock = clock;
    }

    public boolean configured() {
        return Configured.present(settings.iqWealthApiKey());
    }

    /** One GET of {@code path} (for example "/fx"), unless the budget or the provider says not to. */
    public synchronized Reply get(String path) {
        String apiKey = settings.iqWealthApiKey();
        if (!Configured.present(apiKey)) {
            return skipped(Outcome.NO_KEY);
        }
        if (apiKey.equals(refusedKey)) {
            return skipped(Outcome.KEY_REFUSED);
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (today.equals(pausedOn)) {
            return skipped(Outcome.PAUSED_UNTIL_MIDNIGHT);
        }
        if (today.equals(budgetDay) && remaining <= settings.iqWealthReserve()) {
            return skipped(Outcome.BUDGET_KEPT);
        }
        try {
            ResponseEntity<Map> response = client.get().uri(config.url() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toEntity(Map.class);
            countRemaining(today, response.getHeaders().getFirst(REMAINING_HEADER));
            return new Reply(response.getBody(), null);
        } catch (HttpClientErrorException e) {
            return skipped(refused(e, today, apiKey));
        }
    }

    /** What a feed made of its answer (or of the refusal), for the operator dashboard. */
    public Outcome recorded(Outcome outcome) {
        lastPollAt = clock.instant();
        lastOutcome = outcome;
        return outcome;
    }

    /** For the operator dashboard: today's quota as the provider last reported it. */
    public synchronized Quota quota() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Integer left = today.equals(budgetDay) && remaining != Integer.MAX_VALUE ? remaining : null;
        return new Quota(configured(), left, settings.iqWealthReserve(),
                today.equals(pausedOn), settings.iqWealthApiKey().equals(refusedKey), lastPollAt, lastOutcome);
    }

    private Reply skipped(Outcome outcome) {
        return new Reply(null, outcome);
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
}
