package iq.mizan.domain.networth;

import java.util.List;

/** BR-05: assets minus liabilities, with the composition by class (FR-NET-02). Everything in base minor units. */
public final class NetWorth {

    private static final int PERCENT_SCALE = 10;

    private NetWorth() {
    }

    public enum AssetClass {
        CASH,
        METALS,
        RECEIVABLES
    }

    public record Input(long cash, long metals, long receivables, long liabilities) {
    }

    public record Share(AssetClass assetClass, long amount, double percent) {
    }

    public record Result(long totalAssets, long totalLiabilities, long netWorth, List<Share> composition) {
    }

    public static Result compute(Input in) {
        long assets = in.cash() + in.metals() + in.receivables();
        List<Share> composition = List.of(
                new Share(AssetClass.CASH, in.cash(), percent(in.cash(), assets)),
                new Share(AssetClass.METALS, in.metals(), percent(in.metals(), assets)),
                new Share(AssetClass.RECEIVABLES, in.receivables(), percent(in.receivables(), assets)));
        return new Result(assets, in.liabilities(), assets - in.liabilities(), composition);
    }

    // One decimal place, half-up, in integer arithmetic so both implementations agree.
    private static double percent(long amount, long assets) {
        if (assets <= 0) {
            return 0.0;
        }
        return Math.floorDiv(amount * 100 * PERCENT_SCALE * 2 + assets, 2 * assets) / (double) PERCENT_SCALE;
    }
}
