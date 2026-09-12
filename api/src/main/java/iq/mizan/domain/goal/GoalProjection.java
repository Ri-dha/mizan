package iq.mizan.domain.goal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Progress and the two derivable figures of FR-GOL-01/03: months to target, or the monthly amount a date needs. */
public final class GoalProjection {

    private static final int PERCENT_SCALE = 10;

    private GoalProjection() {
    }

    public record Result(long remaining, double percent, Integer monthsToTarget, LocalDate projectedDate, Long requiredMonthly) {
    }

    public static Result compute(long targetAmount, long saved, Long monthlyContribution, LocalDate targetDate, LocalDate today) {
        long remaining = Math.max(0, targetAmount - saved);
        double percent = targetAmount <= 0 || saved >= targetAmount ? 100.0 : roundedPercent(saved, targetAmount);

        Integer months = null;
        LocalDate projected = null;
        if (monthlyContribution != null && monthlyContribution > 0) {
            months = (int) Math.ceilDiv(remaining, monthlyContribution);
            projected = today.plusMonths(months);
        }

        Long required = null;
        if (targetDate != null && remaining > 0) {
            long monthsLeft = ChronoUnit.MONTHS.between(today, targetDate);
            required = monthsLeft <= 0 ? remaining : Math.ceilDiv(remaining, monthsLeft);
        }
        return new Result(remaining, percent, months, projected, required);
    }

    // One decimal place, half-up, in integer arithmetic so both implementations agree.
    private static double roundedPercent(long saved, long target) {
        long tenths = Math.floorDiv(saved * 100 * PERCENT_SCALE * 2 + target, 2 * target);
        return tenths / (double) PERCENT_SCALE;
    }
}
