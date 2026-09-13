package iq.mizan.domain.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import iq.mizan.domain.metal.LotDisposalPlanner;
import iq.mizan.domain.money.LargestRemainderSplit;
import iq.mizan.domain.networth.NetWorth;
import iq.mizan.domain.plan.MonthFigures;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** NFR-06 / OBJ-1: every aggregate equals the sum of its parts, for any input, not just the vectors. */
class ReconciliationProperties {

    private static final long MAX_AMOUNT = 1_000_000_000_000L;

    @Property
    void splitPartsAlwaysSumToTheWhole(@ForAll("totals") long total, @ForAll("shares") List<Integer> shares) {
        List<Long> parts = LargestRemainderSplit.split(total, shares);
        assertThat(parts).hasSize(shares.size());
        assertThat(parts.stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
        assertThat(parts).allMatch(part -> part >= 0);
    }

    @Property
    void allocatedPlusUnallocatedIsTheIncomeAndFreeIsTheIdentity(
            @ForAll("totals") long planned, @ForAll("totals") long received, @ForAll("anyShares") List<Integer> shares,
            @ForAll("amountMaps") Map<Integer, Long> committed, @ForAll("amountMaps") Map<Integer, Long> spent,
            @ForAll("amountMaps") Map<Integer, Long> fixed, @ForAll("amountMaps") Map<Integer, Long> carried) {
        List<MonthFigures.Bucket> buckets = IntStream.range(0, shares.size())
                .mapToObj(i -> new MonthFigures.Bucket("b" + i, shares.get(i), fixed.get(i))).toList();
        MonthFigures.Result result = MonthFigures.compute(new MonthFigures.Input(planned, received, buckets,
                byId(carried), byId(committed), byId(spent), Map.of(), Map.of()));

        long allocated = result.buckets().stream().mapToLong(MonthFigures.BucketFigures::allocated).sum();
        long plannedAllocated = result.buckets().stream().mapToLong(MonthFigures.BucketFigures::plannedAllocated).sum();
        assertThat(allocated + result.unallocatedReceived()).isEqualTo(received);
        assertThat(plannedAllocated + result.unallocatedPlanned()).isEqualTo(planned);
        for (int i = 0; i < buckets.size(); i++) {
            MonthFigures.BucketFigures figures = result.buckets().get(i);
            assertThat(figures.allocated()).isGreaterThanOrEqualTo(0);
            if (buckets.get(i).fixedAmount() != null) {
                assertThat(figures.allocated()).isLessThanOrEqualTo(buckets.get(i).fixedAmount());
            }
            assertThat(figures.free()).isEqualTo(figures.allocated() + figures.carriedIn() + figures.transfersIn() - figures.committed() - figures.spent() - figures.transfersOut());
        }
    }

    @Property
    void aSaleDrawsExactlyTheWeightSoldAndCarriesExactlyTheCostBasisRemoved(
            @ForAll("lots") List<LotDisposalPlanner.Lot> lots, @ForAll("fraction") int fractionPercent, @ForAll("methods") LotDisposalPlanner.Method method) {
        long open = lots.stream().mapToLong(LotDisposalPlanner.Lot::remainingMg).sum();
        long weight = Math.max(1, open * fractionPercent / 100);
        List<String> ids = lots.stream().map(LotDisposalPlanner.Lot::id).toList();

        LotDisposalPlanner.Plan plan = LotDisposalPlanner.plan(lots, weight, method, ids, 0);

        assertThat(plan.allocations().stream().mapToLong(LotDisposalPlanner.Allocation::weightMg).sum()).isEqualTo(weight);
        assertThat(plan.costMetal() + plan.costMaking() + plan.costFees()).isEqualTo(plan.costBasis());
        assertThat(plan.realisedGainExcludingMaking() - plan.realisedGain()).isEqualTo(plan.costMaking());
        for (LotDisposalPlanner.Allocation allocation : plan.allocations()) {
            LotDisposalPlanner.Lot lot = lots.stream().filter(l -> l.id().equals(allocation.lotId())).findFirst().orElseThrow();
            assertThat(allocation.weightMg()).isBetween(1L, lot.remainingMg());
        }
        if (method != LotDisposalPlanner.Method.WEIGHTED_AVERAGE && weight == open) {
            assertThat(plan.costBasis()).isEqualTo(lots.stream().mapToLong(l -> l.metalCost() + l.makingCharge() + l.fees()).sum());
        }
    }

    @Property
    void netWorthIsAssetsMinusLiabilitiesAndSharesCoverTheAssets(
            @ForAll("totals") long cash, @ForAll("totals") long metals, @ForAll("totals") long receivables,
            @ForAll("totals") long vehicles, @ForAll("totals") long property, @ForAll("totals") long liabilities) {
        long illiquid = Math.min(vehicles, property);
        NetWorth.Result result = NetWorth.compute(new NetWorth.Input(cash, metals, receivables, vehicles, property, 0, illiquid, liabilities));
        assertThat(result.totalAssets()).isEqualTo(cash + metals + receivables + vehicles + property);
        assertThat(result.liquidAssets() + result.illiquidAssets()).isEqualTo(result.totalAssets());
        assertThat(result.netWorth()).isEqualTo(result.totalAssets() - result.totalLiabilities());
        assertThat(result.composition().stream().mapToLong(NetWorth.Share::amount).sum()).isEqualTo(result.totalAssets());
        if (result.totalAssets() > 0) {
            double percentSum = result.composition().stream().mapToDouble(NetWorth.Share::percent).sum();
            assertThat(percentSum).isBetween(99.7, 100.3);
        }
    }

    private static Map<String, Long> byId(Map<Integer, Long> byIndex) {
        return byIndex.entrySet().stream().collect(Collectors.toMap(e -> "b" + e.getKey(), Map.Entry::getValue));
    }

    @Provide
    Arbitrary<Long> totals() {
        return Arbitraries.longs().between(0, MAX_AMOUNT);
    }

    /** Shares in basis points that sum to exactly 10,000: random cut points on the line. */
    @Provide
    Arbitrary<List<Integer>> shares() {
        return Arbitraries.integers().between(0, LargestRemainderSplit.BASIS_POINTS).list().ofMinSize(0).ofMaxSize(8)
                .map(cuts -> {
                    List<Integer> sorted = cuts.stream().sorted().toList();
                    List<Integer> parts = new java.util.ArrayList<>();
                    int previous = 0;
                    for (int cut : sorted) {
                        parts.add(cut - previous);
                        previous = cut;
                    }
                    parts.add(LargestRemainderSplit.BASIS_POINTS - previous);
                    return parts;
                });
    }

    @Provide
    Arbitrary<List<Integer>> anyShares() {
        return Arbitraries.integers().between(0, LargestRemainderSplit.BASIS_POINTS).list().ofMaxSize(8);
    }

    @Provide
    Arbitrary<Map<Integer, Long>> amountMaps() {
        return Arbitraries.maps(Arbitraries.integers().between(0, 7), Arbitraries.longs().between(0, MAX_AMOUNT / 1000)).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<LotDisposalPlanner.Lot>> lots() {
        Arbitrary<LotDisposalPlanner.Lot> lot = Combinators.combine(
                        Arbitraries.longs().between(1, 1_000_000),
                        Arbitraries.longs().between(0, 100_000_000),
                        Arbitraries.longs().between(0, 10_000_000),
                        Arbitraries.longs().between(0, 1_000_000),
                        Arbitraries.integers().between(0, 3000))
                .as((weight, metal, making, fees, dayOffset) -> new LotDisposalPlanner.Lot(
                        java.util.UUID.randomUUID().toString(), LocalDate.of(2020, 1, 1).plusDays(dayOffset), weight, weight, metal, making, fees));
        return lot.list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<Integer> fraction() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<LotDisposalPlanner.Method> methods() {
        return Arbitraries.of(LotDisposalPlanner.Method.class);
    }
}
