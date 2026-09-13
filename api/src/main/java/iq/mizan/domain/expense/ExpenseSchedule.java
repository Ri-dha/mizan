package iq.mizan.domain.expense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import iq.mizan.domain.calendar.MonthWindow;

/** Due dates of a bill inside a month (FR-EXP-01): monthly by day, longer cycles from an anchor. */
public final class ExpenseSchedule {

    private static final int QUARTER_MONTHS = 3;
    private static final int YEAR_MONTHS = 12;

    private ExpenseSchedule() {
    }

    public enum Frequency {
        MONTHLY,
        QUARTERLY,
        ANNUAL,
        CUSTOM
    }

    public record Schedule(Frequency frequency, Integer dueDay, LocalDate anchorDate, Integer intervalDays,
                           LocalDate activeFrom, LocalDate activeTo) {

        boolean isActiveOn(LocalDate date) {
            return !date.isBefore(activeFrom) && (activeTo == null || !date.isAfter(activeTo));
        }
    }

    public static List<LocalDate> dueDates(Schedule schedule, MonthWindow window) {
        return candidates(schedule, window).stream().filter(schedule::isActiveOn).toList();
    }

    private static List<LocalDate> candidates(Schedule schedule, MonthWindow window) {
        return switch (schedule.frequency()) {
            case MONTHLY -> monthly(schedule.dueDay() == null ? 1 : schedule.dueDay(), window);
            case QUARTERLY -> monthSteps(schedule.anchorDate(), QUARTER_MONTHS, window);
            case ANNUAL -> monthSteps(schedule.anchorDate(), YEAR_MONTHS, window);
            case CUSTOM -> cycle(schedule.anchorDate(), Math.max(1, schedule.intervalDays() == null ? 1 : schedule.intervalDays()), window);
        };
    }

    private static List<LocalDate> monthly(int dueDay, MonthWindow window) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth month = YearMonth.from(window.from());
        YearMonth last = YearMonth.from(window.toExclusive().minusDays(1));
        while (!month.isAfter(last)) {
            LocalDate candidate = month.atDay(Math.min(dueDay, month.lengthOfMonth()));
            if (window.contains(candidate)) {
                dates.add(candidate);
            }
            month = month.plusMonths(1);
        }
        return dates;
    }

    private static List<LocalDate> monthSteps(LocalDate anchor, int stepMonths, MonthWindow window) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth month = YearMonth.from(anchor);
        YearMonth last = YearMonth.from(window.toExclusive().minusDays(1));
        while (!month.isAfter(last)) {
            LocalDate candidate = month.atDay(Math.min(anchor.getDayOfMonth(), month.lengthOfMonth()));
            if (window.contains(candidate)) {
                dates.add(candidate);
            }
            month = month.plusMonths(stepMonths);
        }
        return dates;
    }

    private static List<LocalDate> cycle(LocalDate anchor, int stepDays, MonthWindow window) {
        LocalDate date = anchor;
        if (anchor.isBefore(window.from())) {
            long gap = ChronoUnit.DAYS.between(anchor, window.from());
            date = anchor.plusDays(Math.ceilDiv(gap, stepDays) * stepDays);
        }
        List<LocalDate> dates = new ArrayList<>();
        while (date.isBefore(window.toExclusive())) {
            dates.add(date);
            date = date.plusDays(stepDays);
        }
        return dates;
    }
}
