package iq.mizan.market.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.market.MarketProperties;
import iq.mizan.market.dto.HistoryPointResponse;
import iq.mizan.market.dto.PriceChangeResponse;
import iq.mizan.market.dto.PriceChangesResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceHistory;
import iq.mizan.market.entity.PriceTick;
import iq.mizan.market.repository.PriceHistoryRepository;
import iq.mizan.market.repository.PriceTickRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads what {@link MarketService} stored: daily ranges rolled up by week or month, and every change. */
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    public static final int MAX_CHANGES_PAGE = 500;

    private final PriceHistoryRepository history;
    private final PriceTickRepository ticks;
    private final MarketProperties properties;
    private final Clock clock;
    private final MarketCache cache;

    @Cacheable(MarketCache.HISTORY)
    @Transactional(readOnly = true)
    public List<HistoryPointResponse> history(Instrument instrument, LocalDate from, LocalDate to, HistoryInterval interval) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(properties.historyDefaultDays()) : from;
        if (start.plusDays(properties.historyMaxDays()).isBefore(end)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "History is limited to %d days per request".formatted(properties.historyMaxDays()));
        }
        List<PriceHistory> days = history.findByKeyInstrumentAndKeyDayBetweenOrderByKeyDay(instrument, start, end);
        return rollUp(days, interval);
    }

    static List<HistoryPointResponse> rollUp(List<PriceHistory> days, HistoryInterval interval) {
        Map<LocalDate, List<PriceHistory>> periods = new LinkedHashMap<>();
        for (PriceHistory day : days) {
            periods.computeIfAbsent(interval.periodStart(day.getKey().getDay()), key -> new ArrayList<>()).add(day);
        }
        List<HistoryPointResponse> points = new ArrayList<>();
        periods.forEach((start, members) -> {
            PriceHistory first = members.getFirst();
            PriceHistory last = members.getLast();
            long high = members.stream().mapToLong(PriceHistory::getHighMicros).max().orElseThrow();
            long low = members.stream().mapToLong(PriceHistory::getLowMicros).min().orElseThrow();
            points.add(new HistoryPointResponse(start, first.getOpenMicros(), high, low, last.getPriceMicros(), last.getSource()));
        });
        return points;
    }

    @Cacheable(MarketCache.CHANGES)
    @Transactional(readOnly = true)
    public PriceChangesResponse changes(Instrument instrument, Instant from, Instant before, int limit) {
        Instant end = before == null ? clock.instant().plusSeconds(1) : before;
        Instant start = from == null ? Instant.EPOCH : from;
        int size = Math.clamp(limit, 1, MAX_CHANGES_PAGE);
        List<PriceTick> page = ticks.findByInstrumentAndObservedAtGreaterThanEqualAndObservedAtLessThanOrderByObservedAtDesc(
                instrument, start, end, Limit.of(size + 1));
        boolean more = page.size() > size;
        List<PriceTick> shown = more ? page.subList(0, size) : page;
        List<PriceChangeResponse> changes = new ArrayList<>(shown.size());
        for (int i = 0; i < shown.size(); i++) {
            PriceTick tick = shown.get(i);
            Long previous = i + 1 < page.size()
                    ? Long.valueOf(page.get(i + 1).getPriceMicros())
                    : ticks.findFirstByInstrumentAndObservedAtLessThanOrderByObservedAtDesc(instrument, tick.getObservedAt())
                            .map(PriceTick::getPriceMicros).orElse(null);
            changes.add(new PriceChangeResponse(tick.getId(), tick.getObservedAt(), tick.getPriceMicros(), previous, tick.getSource()));
        }
        return new PriceChangesResponse(changes, more ? shown.getLast().getObservedAt() : null);
    }

    /** An operator removing a bad price; the caller audits it with the reason. Returns what was removed. */
    @Transactional
    public PriceChangeResponse deleteTick(long id) {
        PriceTick tick = ticks.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such price change"));
        ticks.delete(tick);
        cache.clearAfterCommit();
        return new PriceChangeResponse(tick.getId(), tick.getObservedAt(), tick.getPriceMicros(), null, tick.getSource());
    }

    public Instrument instrumentOf(long id) {
        return ticks.findById(id).map(PriceTick::getInstrument).orElse(null);
    }
}
