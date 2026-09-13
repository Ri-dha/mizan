package iq.mizan.domain.debt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-DBT-07: the same debts paid in two orders. Every debt gets its own payment each month; the
 * extra amount and the payments of debts already cleared go to the first open debt in the order.
 * A comparison of outcomes, never advice.
 */
public final class DebtStrategy {

    private static final int MAX_MONTHS = DebtPayoff.MAX_MONTHS;

    private DebtStrategy() {
    }

    public record Debt(String id, long balance, int annualRateBasisPoints, long monthlyPayment) {
    }

    public record Outcome(List<String> order, int months, long totalInterest, boolean neverClears) {
    }

    public record Result(Outcome snowball, Outcome avalanche) {
    }

    public static Result compare(List<Debt> debts, long extraMonthly) {
        List<String> snowball = debts.stream().sorted(Comparator.comparingLong(Debt::balance).thenComparing(Debt::id)).map(Debt::id).toList();
        List<String> avalanche = debts.stream()
                .sorted(Comparator.comparingInt(Debt::annualRateBasisPoints).reversed().thenComparingLong(Debt::balance).thenComparing(Debt::id))
                .map(Debt::id).toList();
        return new Result(simulate(debts, extraMonthly, snowball), simulate(debts, extraMonthly, avalanche));
    }

    static Outcome simulate(List<Debt> debts, long extraMonthly, List<String> order) {
        Map<String, Debt> byId = new HashMap<>();
        Map<String, Long> balance = new HashMap<>();
        for (Debt debt : debts) {
            byId.put(debt.id(), debt);
            balance.put(debt.id(), debt.balance());
        }
        int months = 0;
        long totalInterest = 0;
        while (order.stream().anyMatch(id -> balance.get(id) > 0)) {
            if (months >= MAX_MONTHS) {
                return new Outcome(order, 0, 0, true);
            }
            months++;
            long freed = extraMonthly;
            for (String id : order) {
                if (balance.get(id) > 0) {
                    long interest = DebtPayoff.monthlyInterest(balance.get(id), byId.get(id).annualRateBasisPoints());
                    totalInterest += interest;
                    balance.merge(id, interest, Long::sum);
                }
            }
            for (String id : order) {
                if (balance.get(id) <= 0) {
                    freed += byId.get(id).monthlyPayment();
                } else {
                    balance.merge(id, -Math.min(byId.get(id).monthlyPayment(), balance.get(id)), Long::sum);
                }
            }
            for (String id : order) {
                if (balance.get(id) > 0 && freed > 0) {
                    long use = Math.min(freed, balance.get(id));
                    balance.merge(id, -use, Long::sum);
                    freed -= use;
                }
            }
        }
        return new Outcome(new ArrayList<>(order), months, totalInterest, false);
    }
}
