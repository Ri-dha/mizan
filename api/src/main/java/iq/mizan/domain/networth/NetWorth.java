package iq.mizan.domain.networth;

import java.util.List;

/**
 * BR-05: assets minus liabilities, with the composition by class (FR-NET-02) and the liquid
 * split (FR-NET-06). Everything in base minor units.
 */
public final class NetWorth {

    private static final int PERCENT_SCALE = 10;

    private NetWorth() {
    }

    public enum AssetClass {
        CASH,
        METALS,
        RECEIVABLES,
        VEHICLES,
        PROPERTY,
        OTHER_ASSETS
    }

    /** {@code illiquid} is the part of vehicles, property and other assets the household marked illiquid. */
    public record Input(long cash, long metals, long receivables, long vehicles, long property, long otherAssets,
                        long illiquid, long liabilities) {
    }

    public record Share(AssetClass assetClass, long amount, double percent) {
    }

    public record Result(long totalAssets, long totalLiabilities, long netWorth, long liquidAssets, long illiquidAssets,
                         List<Share> composition) {
    }

    public static Result compute(Input in) {
        long assets = in.cash() + in.metals() + in.receivables() + in.vehicles() + in.property() + in.otherAssets();
        List<Share> composition = List.of(
                share(AssetClass.CASH, in.cash(), assets),
                share(AssetClass.METALS, in.metals(), assets),
                share(AssetClass.RECEIVABLES, in.receivables(), assets),
                share(AssetClass.VEHICLES, in.vehicles(), assets),
                share(AssetClass.PROPERTY, in.property(), assets),
                share(AssetClass.OTHER_ASSETS, in.otherAssets(), assets));
        return new Result(assets, in.liabilities(), assets - in.liabilities(), assets - in.illiquid(), in.illiquid(), composition);
    }

    private static Share share(AssetClass assetClass, long amount, long assets) {
        return new Share(assetClass, amount, percent(amount, assets));
    }

    // One decimal place, half-up, in integer arithmetic so both implementations agree.
    private static double percent(long amount, long assets) {
        if (assets <= 0) {
            return 0.0;
        }
        return Math.floorDiv(amount * 100 * PERCENT_SCALE * 2 + assets, 2 * assets) / (double) PERCENT_SCALE;
    }
}
