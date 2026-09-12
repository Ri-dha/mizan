package iq.mizan.domain.calendar;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A household month (FR-SET-05): starts on the configured day of the calendar month named by
 * its key and ends the day before that day in the next one. Keyed by the calendar month it
 * starts in, so "2026-09" with start day 25 runs 25 Sep to 24 Oct.
 */
public record MonthWindow(String key, LocalDate from, LocalDate toExclusive) {

    public static MonthWindow of(String key, int startDay) {
        YearMonth month = YearMonth.parse(key);
        return new MonthWindow(key, startOf(month, startDay), startOf(month.plusMonths(1), startDay));
    }

    public static String keyFor(LocalDate date, int startDay) {
        YearMonth month = YearMonth.from(date);
        if (date.isBefore(startOf(month, startDay))) {
            month = month.minusMonths(1);
        }
        return month.toString();
    }

    public static String previous(String key) {
        return YearMonth.parse(key).minusMonths(1).toString();
    }

    public static String next(String key) {
        return YearMonth.parse(key).plusMonths(1).toString();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && date.isBefore(toExclusive);
    }

    private static LocalDate startOf(YearMonth month, int startDay) {
        return month.atDay(Math.min(startDay, month.lengthOfMonth()));
    }
}
