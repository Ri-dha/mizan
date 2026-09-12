package iq.mizan.domain.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iq.mizan.domain.money.LargestRemainderSplit;

/**
 * The per-bucket view of a month (FR-PLN-05): what each bucket was allocated from planned and
 * from received income, what it has committed, spent and transferred, and what is free.
 * Transfers are never spending (BR-04) but they do move money out of a bucket.
 */
public final class MonthFigures {

    private MonthFigures() {
    }

    public record Bucket(String id, int shareBasisPoints) {
    }

    public record Input(long plannedIncome, long receivedIncome, List<Bucket> buckets,
                        Map<String, Long> committed, Map<String, Long> spent,
                        Map<String, Long> transfersIn, Map<String, Long> transfersOut) {
    }

    public record BucketFigures(String id, long plannedAllocated, long allocated, long committed, long spent,
                                long transfersIn, long transfersOut, long free) {
    }

    public record Result(List<BucketFigures> buckets, int totalShareBasisPoints,
                         long unallocatedPlanned, long unallocatedReceived) {
    }

    public static Result compute(Input input) {
        List<Integer> shares = input.buckets().stream().map(Bucket::shareBasisPoints).toList();
        List<Long> planned = allocate(input.plannedIncome(), shares);
        List<Long> received = allocate(input.receivedIncome(), shares);

        List<BucketFigures> figures = new ArrayList<>();
        for (int i = 0; i < input.buckets().size(); i++) {
            String id = input.buckets().get(i).id();
            long committed = input.committed().getOrDefault(id, 0L);
            long spent = input.spent().getOrDefault(id, 0L);
            long in = input.transfersIn().getOrDefault(id, 0L);
            long out = input.transfersOut().getOrDefault(id, 0L);
            long allocated = received.get(i);
            figures.add(new BucketFigures(id, planned.get(i), allocated, committed, spent, in, out,
                    allocated + in - committed - spent - out));
        }

        int totalShares = shares.stream().mapToInt(Integer::intValue).sum();
        return new Result(
                figures,
                totalShares,
                input.plannedIncome() - planned.stream().mapToLong(Long::longValue).sum(),
                input.receivedIncome() - received.stream().mapToLong(Long::longValue).sum());
    }

    /**
     * A plan that sums to 100% splits exactly (BR-02). One that does not gets each share's floor,
     * and the difference surfaces as the unallocated amount the warning shows (FR-PLN-04).
     */
    static List<Long> allocate(long total, List<Integer> sharesBasisPoints) {
        int sum = sharesBasisPoints.stream().mapToInt(Integer::intValue).sum();
        if (sum == LargestRemainderSplit.BASIS_POINTS) {
            return LargestRemainderSplit.split(total, sharesBasisPoints);
        }
        return sharesBasisPoints.stream()
                .map(share -> Math.floorDiv(total * share, LargestRemainderSplit.BASIS_POINTS))
                .toList();
    }
}
