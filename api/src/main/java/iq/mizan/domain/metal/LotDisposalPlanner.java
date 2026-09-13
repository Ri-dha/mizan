package iq.mizan.domain.metal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which lots a sale draws from and what cost basis leaves with the metal (BR-11).
 * Making charges and fees travel as their own components so the gain can be shown with and
 * without them (BR-12). Weighted average draws metal oldest-first but prices every gram at
 * the average cost of everything open.
 */
public final class LotDisposalPlanner {

    private LotDisposalPlanner() {
    }

    public enum Method {
        FIFO,
        SPECIFIC,
        WEIGHTED_AVERAGE
    }

    public record Lot(String id, LocalDate purchaseDate, long weightMg, long remainingMg,
                      long metalCost, long makingCharge, long fees) {
    }

    public record Allocation(String lotId, long weightMg, long metalCost, long makingCharge, long fees) {
    }

    public record Plan(List<Allocation> allocations, long costMetal, long costMaking, long costFees, long costBasis,
                       long realisedGain, long realisedGainExcludingMaking) {
    }

    public static class InsufficientWeightException extends RuntimeException {
        public InsufficientWeightException() {
            super("Not enough open metal to sell that weight");
        }
    }

    public static Plan plan(List<Lot> lots, long weightMg, Method method, List<String> specificLotIds, long proceeds) {
        long openTotal = lots.stream().mapToLong(Lot::remainingMg).sum();
        if (weightMg <= 0 || weightMg > openTotal) {
            throw new InsufficientWeightException();
        }

        List<Lot> order = order(lots, method, specificLotIds);
        long totalMetal = lots.stream().mapToLong(Lot::metalCost).sum();
        long totalMaking = lots.stream().mapToLong(Lot::makingCharge).sum();
        long totalFees = lots.stream().mapToLong(Lot::fees).sum();

        List<Allocation> allocations = new ArrayList<>();
        long left = weightMg;
        for (Lot lot : order) {
            if (left <= 0) {
                break;
            }
            long take = Math.min(left, lot.remainingMg());
            if (take <= 0) {
                continue;
            }
            Allocation allocation = method == Method.WEIGHTED_AVERAGE
                    ? new Allocation(lot.id(), take, share(totalMetal, take, openTotal), share(totalMaking, take, openTotal), share(totalFees, take, openTotal))
                    : take == lot.remainingMg()
                            ? new Allocation(lot.id(), take, lot.metalCost(), lot.makingCharge(), lot.fees())
                            : new Allocation(lot.id(), take, share(lot.metalCost(), take, lot.remainingMg()),
                                    share(lot.makingCharge(), take, lot.remainingMg()), share(lot.fees(), take, lot.remainingMg()));
            allocations.add(allocation);
            left -= take;
        }
        if (left > 0) {
            throw new InsufficientWeightException();
        }

        long costMetal = allocations.stream().mapToLong(Allocation::metalCost).sum();
        long costMaking = allocations.stream().mapToLong(Allocation::makingCharge).sum();
        long costFees = allocations.stream().mapToLong(Allocation::fees).sum();
        long costBasis = costMetal + costMaking + costFees;
        return new Plan(List.copyOf(allocations), costMetal, costMaking, costFees, costBasis,
                proceeds - costBasis, proceeds - (costMetal + costFees));
    }

    private static List<Lot> order(List<Lot> lots, Method method, List<String> specificLotIds) {
        if (method == Method.SPECIFIC) {
            Map<String, Lot> byId = lots.stream().collect(Collectors.toMap(Lot::id, Function.identity()));
            return specificLotIds.stream().map(byId::get).filter(lot -> lot != null).toList();
        }
        return lots.stream()
                .filter(lot -> lot.remainingMg() > 0)
                .sorted(Comparator.comparing(Lot::purchaseDate).thenComparing(Lot::id))
                .toList();
    }

    private static long share(long amount, long part, long whole) {
        return Math.floorDiv(amount * part + whole / 2, whole);
    }
}
