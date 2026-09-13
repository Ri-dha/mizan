package iq.mizan.domain.asset;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * BR-13: an asset's current value runs from its latest manual valuation (or its purchase) and
 * declines month by month. Whole minor units; the monthly step is rounded half-up so both
 * implementations agree.
 */
public final class Depreciation {

    private static final long BASIS_POINTS = 10_000;
    private static final long MONTHS_PER_YEAR = 12;

    private Depreciation() {
    }

    public enum Method {
        NONE,
        STRAIGHT_LINE,
        DECLINING_BALANCE
    }

    public record Input(long baselineValue, LocalDate baselineDate, long salvageValue, int annualRateBasisPoints,
                        Method method, LocalDate asOf) {
    }

    public record Result(long value, int monthsElapsed) {
    }

    public static Result compute(Input in) {
        int months = (int) Math.max(0, ChronoUnit.MONTHS.between(in.baselineDate(), in.asOf()));
        long floor = Math.min(in.salvageValue(), in.baselineValue());
        long value = switch (in.method()) {
            case NONE -> in.baselineValue();
            case STRAIGHT_LINE -> in.baselineValue() - monthlyStep(in.baselineValue(), in.annualRateBasisPoints()) * months;
            case DECLINING_BALANCE -> declining(in.baselineValue(), in.annualRateBasisPoints(), months, floor);
        };
        return new Result(Math.max(floor, value), months);
    }

    private static long declining(long baseline, int rateBasisPoints, int months, long floor) {
        long value = baseline;
        for (int month = 0; month < months && value > floor; month++) {
            value -= monthlyStep(value, rateBasisPoints);
        }
        return value;
    }

    private static long monthlyStep(long value, int rateBasisPoints) {
        long divisor = BASIS_POINTS * MONTHS_PER_YEAR;
        return Math.floorDiv(value * rateBasisPoints + divisor / 2, divisor);
    }
}
