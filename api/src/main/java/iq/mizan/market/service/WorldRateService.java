package iq.mizan.market.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.market.WorldRateProperties;
import iq.mizan.market.dto.WorldRatePointResponse;
import iq.mizan.market.dto.WorldRatesResponse;
import iq.mizan.market.feed.FrankfurterClient;
import iq.mizan.market.feed.FrankfurterClient.DayRate;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-world-rates: fetched for everyone a few times a day and served from the table. Each fetch
 * asks again for the last few days, because a central bank's rate can arrive or be corrected late.
 */
@Service
@RequiredArgsConstructor
public class WorldRateService {

    static final int REFETCH_DAYS = 5;
    public static final int MAX_HISTORY_DAYS = 1830;
    private static final int BATCH = 1000;
    private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

    private final FrankfurterClient frankfurter;
    private final WorldRateProperties properties;
    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final MeterRegistry meters;
    private final MarketCache cache;
    private final Clock clock;

    public void poll() {
        try {
            store(frankfurter.since(fetchFrom()));
            meters.counter("mizan.world_rates.polled", "outcome", "ok").increment();
        } catch (RuntimeException e) {
            meters.counter("mizan.world_rates.polled", "outcome", "failed").increment();
            throw e;
        }
    }

    LocalDate fetchFrom() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return jdbc.sql("select max(day) from world_rate").query(LocalDate.class).optional()
                .map(latest -> latest.minusDays(REFETCH_DAYS))
                .orElse(today.minusDays(properties.historyDays()));
    }

    @Transactional
    public void store(List<DayRate> rates) {
        Timestamp now = Timestamp.from(clock.instant());
        template.batchUpdate("""
                insert into world_rate (currency, day, per_usd, source, fetched_at) values (?, ?, ?, ?, ?)
                on conflict (currency, day) do update set per_usd = excluded.per_usd, source = excluded.source, fetched_at = excluded.fetched_at
                """, rates, BATCH, (statement, rate) -> {
            statement.setString(1, rate.currency());
            statement.setDate(2, Date.valueOf(rate.day()));
            statement.setBigDecimal(3, rate.perUsd());
            statement.setString(4, FrankfurterClient.NAME);
            statement.setTimestamp(5, now);
        });
        cache.clearAfterCommit();
    }

    /** Whole units of {@code to} per unit of {@code from} from the stored rates; the dinar at the Baghdad market dollar. */
    @Transactional(readOnly = true)
    public java.util.Optional<BigDecimal> estimateFactor(String from, String to) {
        java.util.Optional<BigDecimal> fromPerUsd = perUsd(from);
        java.util.Optional<BigDecimal> toPerUsd = perUsd(to);
        if (fromPerUsd.isEmpty() || toPerUsd.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(toPerUsd.get().divide(fromPerUsd.get(), java.math.MathContext.DECIMAL64));
    }

    private java.util.Optional<BigDecimal> perUsd(String currency) {
        if ("USD".equals(currency)) {
            return java.util.Optional.of(BigDecimal.ONE);
        }
        if ("IQD".equals(currency)) {
            return jdbc.sql("select price_micros from price_quote where instrument in ('USDIQD_PARALLEL', 'USDIQD_OFFICIAL') order by instrument desc limit 1")
                    .query(Long.class).optional().map(micros -> BigDecimal.valueOf(micros, 6));
        }
        return jdbc.sql("select per_usd from world_rate where currency = ? order by day desc limit 1").param(currency).query(BigDecimal.class).optional();
    }

    @Cacheable(MarketCache.WORLD_RATES)
    @Transactional(readOnly = true)
    public WorldRatesResponse latest() {
        List<WorldRatesResponse.WorldRate> rates = jdbc.sql("""
                select distinct on (currency) currency, per_usd, day from world_rate order by currency, day desc
                """).query((rs, row) -> new WorldRatesResponse.WorldRate(rs.getString(1), rs.getBigDecimal(2), rs.getObject(3, LocalDate.class))).list();
        return new WorldRatesResponse(FrankfurterClient.NAME, rates);
    }

    @Cacheable(MarketCache.WORLD_RATE_HISTORY)
    @Transactional(readOnly = true)
    public List<WorldRatePointResponse> history(String currency, LocalDate from, LocalDate to) {
        if (!CODE.matcher(currency).matches()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "A currency is three capital letters, such as EUR");
        }
        LocalDate end = to == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : to;
        LocalDate start = from == null ? end.minusDays(365) : from;
        if (start.isAfter(end) || start.isBefore(end.minusDays(MAX_HISTORY_DAYS))) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Choose a range of at most %d days".formatted(MAX_HISTORY_DAYS));
        }
        return jdbc.sql("select day, per_usd from world_rate where currency = ? and day between ? and ? order by day")
                .param(currency).param(start).param(end)
                .query((rs, row) -> new WorldRatePointResponse(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2))).list();
    }
}
