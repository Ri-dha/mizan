package iq.mizan.market.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum HistoryInterval {
    DAY,
    WEEK,
    MONTH;

    LocalDate periodStart(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }
}
