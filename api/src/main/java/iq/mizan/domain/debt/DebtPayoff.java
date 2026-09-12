package iq.mizan.domain.debt;

/**
 * Amortisation by simulation in whole minor units (FR-DBT-03): each month charges interest at
 * the annual rate over twelve, rounded half-up, then applies the payment. Simulating rather
 * than solving the closed form is what lets the client and the server agree to the dinar.
 */
public final class DebtPayoff {

    /** Basis points per year → the divisor that turns balance × bp into monthly interest. */
    static final long MONTHLY_DIVISOR = 10_000L * 12;
    static final int MAX_MONTHS = 1200;

    private DebtPayoff() {
    }

    public record Result(boolean neverClears, int months, long totalPaid, long totalInterest) {

        static final Result NEVER = new Result(true, 0, 0, 0);
    }

    public static Result compute(long balance, int annualRateBasisPoints, long monthlyPayment) {
        if (balance <= 0) {
            return new Result(false, 0, 0, 0);
        }
        int months = 0;
        long paid = 0;
        long interestTotal = 0;
        long remaining = balance;
        while (remaining > 0) {
            long interest = monthlyInterest(remaining, annualRateBasisPoints);
            if (monthlyPayment <= interest || months >= MAX_MONTHS) {
                return Result.NEVER;
            }
            months++;
            long due = Math.min(monthlyPayment, remaining + interest);
            paid += due;
            interestTotal += interest;
            remaining = remaining + interest - due;
        }
        return new Result(false, months, paid, interestTotal);
    }

    public static long monthlyInterest(long balance, int annualRateBasisPoints) {
        return Math.floorDiv(balance * annualRateBasisPoints + MONTHLY_DIVISOR / 2, MONTHLY_DIVISOR);
    }
}
