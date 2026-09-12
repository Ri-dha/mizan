package iq.mizan.domain.income;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import iq.mizan.domain.calendar.MonthWindow;

/** Which pay dates fall inside a month, and what they are worth in the base currency (FR-INC-03). */
public final class PlannedIncome {

    private static final int WEEK_DAYS = 7;
    private static final int TWO_WEEKS_DAYS = 14;

    private PlannedIncome() {
    }

    public record Source(
            long baseAmount, Frequency frequency, Integer payDay, LocalDate anchorDate,
            LocalDate activeFrom, LocalDate activeTo) {

        boolean isActiveOn(LocalDate date) {
            return !date.isBefore(activeFrom) && (activeTo == null || !date.isAfter(activeTo));
        }
    }

    public record Occurrence(int sourceIndex, LocalDate date, long amount) {
    }

    public record Result(long total, List<Occurrence> occurrences) {
    }

    public static Result compute(List<Source> sources, MonthWindow window) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Source source = sources.get(i);
            for (LocalDate date : payDates(source, window)) {
                if (source.isActiveOn(date)) {
                    occurrences.add(new Occurrence(i, date, source.baseAmount()));
                }
            }
        }
        occurrences.sort(Comparator.comparing(Occurrence::date).thenComparingInt(Occurrence::sourceIndex));
        long total = occurrences.stream().mapToLong(Occurrence::amount).sum();
        return new Result(total, List.copyOf(occurrences));
    }

    private static List<LocalDate> payDates(Source source, MonthWindow window) {
        return switch (source.frequency()) {
            case MONTHLY -> monthlyDates(source.payDay(), window);
            case WEEKLY -> cycleDates(source.anchorDate(), WEEK_DAYS, window);
            case BIWEEKLY -> cycleDates(source.anchorDate(), TWO_WEEKS_DAYS, window);
            case ONE_OFF -> window.contains(source.anchorDate()) ? List.of(source.anchorDate()) : List.of();
        };
    }

    // A window spans at most two calendar months; the pay day exists once in each.
    private static List<LocalDate> monthlyDates(int payDay, MonthWindow window) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth month = YearMonth.from(window.from());
        YearMonth last = YearMonth.from(window.toExclusive().minusDays(1));
        while (!month.isAfter(last)) {
            LocalDate candidate = month.atDay(Math.min(payDay, month.lengthOfMonth()));
            if (window.contains(candidate)) {
                dates.add(candidate);
            }
            month = month.plusMonths(1);
        }
        return dates;
    }

    private static List<LocalDate> cycleDates(LocalDate anchor, int stepDays, MonthWindow window) {
        LocalDate date = anchor;
        if (anchor.isBefore(window.from())) {
            long cycles = Math.ceilDiv(ChronoUnit.DAYS.between(anchor, window.from()), stepDays);
            date = anchor.plusDays(cycles * stepDays);
        }
        List<LocalDate> dates = new ArrayList<>();
        while (date.isBefore(window.toExclusive())) {
            dates.add(date);
            date = date.plusDays(stepDays);
        }
        return dates;
    }
}
