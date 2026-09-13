package iq.mizan.domain.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iq.mizan.domain.money.LargestRemainderSplit;

/**
 * The per-bucket view of a month (FR-PLN-05): what each bucket was allocated from planned and
 * from received income, what it carried in from last month, what it has committed, spent and
 * transferred, and what is free. Transfers are never spending (BR-04) but they do move money
 * out of a bucket.
 */
public final class MonthFigures {

    private MonthFigures() {
    }

    /** A bucket with a fixed amount is funded first and takes no share of the remainder (FR-PLN-07). */
    public record Bucket(String id, int shareBasisPoints, Long fixedAmount) {
    }

    public record Input(long plannedIncome, long receivedIncome, List<Bucket> buckets,
                        Map<String, Long> carriedIn, Map<String, Long> committed, Map<String, Long> spent,
                        Map<String, Long> transfersIn, Map<String, Long> transfersOut) {
    }

    public record BucketFigures(String id, long plannedAllocated, long allocated, long carriedIn, long committed, long spent,
                                long transfersIn, long transfersOut, long free) {
    }

    public record Result(List<BucketFigures> buckets, int totalShareBasisPoints, long fixedTotal,
                         long unallocatedPlanned, long unallocatedReceived) {
    }

    public static Result compute(Input input) {
        List<Long> planned = allocate(input.plannedIncome(), input.buckets());
        List<Long> received = allocate(input.receivedIncome(), input.buckets());

        List<BucketFigures> figures = new ArrayList<>();
        for (int i = 0; i < input.buckets().size(); i++) {
            String id = input.buckets().get(i).id();
            long carriedIn = input.carriedIn().getOrDefault(id, 0L);
            long committed = input.committed().getOrDefault(id, 0L);
            long spent = input.spent().getOrDefault(id, 0L);
            long in = input.transfersIn().getOrDefault(id, 0L);
            long out = input.transfersOut().getOrDefault(id, 0L);
            long allocated = received.get(i);
            figures.add(new BucketFigures(id, planned.get(i), allocated, carriedIn, committed, spent, in, out,
                    allocated + carriedIn + in - committed - spent - out));
        }

        int totalShares = input.buckets().stream().filter(b -> b.fixedAmount() == null).mapToInt(Bucket::shareBasisPoints).sum();
        long fixedTotal = input.buckets().stream().filter(b -> b.fixedAmount() != null).mapToLong(Bucket::fixedAmount).sum();
        return new Result(
                figures,
                totalShares,
                fixedTotal,
                input.plannedIncome() - planned.stream().mapToLong(Long::longValue).sum(),
                input.receivedIncome() - received.stream().mapToLong(Long::longValue).sum());
    }

    /**
     * Fixed amounts are funded first, in bucket order, until the income runs out; the remainder
     * splits by percentage. Shares that sum to 100% split exactly (BR-02); shares that do not get
     * each share's floor, and the difference surfaces as the unallocated amount (FR-PLN-04).
     */
    static List<Long> allocate(long total, List<Bucket> buckets) {
        long remaining = total;
        List<Long> fixed = new ArrayList<>();
        List<Integer> shares = new ArrayList<>();
        for (Bucket bucket : buckets) {
            if (bucket.fixedAmount() != null) {
                long funded = Math.max(0, Math.min(bucket.fixedAmount(), remaining));
                remaining -= funded;
                fixed.add(funded);
            } else {
                fixed.add(null);
                shares.add(bucket.shareBasisPoints());
            }
        }
        List<Long> split = splitShares(remaining, shares);
        List<Long> allocated = new ArrayList<>();
        int shareIndex = 0;
        for (Long amount : fixed) {
            allocated.add(amount != null ? amount : split.get(shareIndex++));
        }
        return allocated;
    }

    private static List<Long> splitShares(long total, List<Integer> sharesBasisPoints) {
        int sum = sharesBasisPoints.stream().mapToInt(Integer::intValue).sum();
        if (sum == LargestRemainderSplit.BASIS_POINTS) {
            return LargestRemainderSplit.split(total, sharesBasisPoints);
        }
        return sharesBasisPoints.stream()
                .map(share -> Math.floorDiv(total * share, LargestRemainderSplit.BASIS_POINTS))
                .toList();
    }
}
