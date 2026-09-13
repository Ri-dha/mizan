package iq.mizan.domain.metal;

import java.math.BigInteger;

/**
 * BR-08 in exact integer arithmetic: spot USD per troy ounce → IQD per gram of pure metal →
 * the lot's purity → the local premium → the lot's weight. Every intermediate is in micros and
 * rounds half-up at the same four points on both platforms, so a lot is worth the same dinar
 * on the phone and on the server.
 */
public final class MetalValuation {

    public static final long MICROGRAMS_PER_TROY_OUNCE = 31_103_500L;
    private static final long MICRO = 1_000_000L;
    private static final long BASIS_POINTS = 10_000L;
    private static final long MICROS_PER_GRAM_MILLIGRAM = 1_000_000_000L;

    private MetalValuation() {
    }

    /** {@code overridePerGram24kMicros}, when present, is a user-entered IQD price per gram of pure metal that replaces spot × rate (FR-MTL-09). */
    public record Input(long spotUsdPerOzMicros, long usdIqdMicros, int purityBasisPoints,
                        int premiumBasisPoints, long premiumFixedPerGramMicros, long weightMg, Long overridePerGram24kMicros,
                        int discountBasisPoints) {

        public Input(long spotUsdPerOzMicros, long usdIqdMicros, int purityBasisPoints, int premiumBasisPoints,
                     long premiumFixedPerGramMicros, long weightMg, Long overridePerGram24kMicros) {
            this(spotUsdPerOzMicros, usdIqdMicros, purityBasisPoints, premiumBasisPoints, premiumFixedPerGramMicros, weightMg, overridePerGram24kMicros, 0);
        }
    }

    public record Result(long perGram24kMicros, long perGramMicros, long value) {
    }

    public static Result compute(Input in) {
        long perGram24k = in.overridePerGram24kMicros() != null
                ? in.overridePerGram24kMicros()
                : halfUp(BigInteger.valueOf(in.spotUsdPerOzMicros()).multiply(BigInteger.valueOf(in.usdIqdMicros())), MICROGRAMS_PER_TROY_OUNCE);
        long perGramPurity = halfUp(BigInteger.valueOf(perGram24k).multiply(BigInteger.valueOf(in.purityBasisPoints())), BASIS_POINTS);
        long perGramMarket = halfUp(BigInteger.valueOf(perGramPurity).multiply(BigInteger.valueOf(BASIS_POINTS + in.premiumBasisPoints())), BASIS_POINTS)
                + in.premiumFixedPerGramMicros();
        // FR-MTL-11: what a dealer would pay is the market price less the buy-back spread.
        long perGram = in.discountBasisPoints() == 0 ? perGramMarket
                : halfUp(BigInteger.valueOf(perGramMarket).multiply(BigInteger.valueOf(BASIS_POINTS - in.discountBasisPoints())), BASIS_POINTS);
        long value = halfUp(BigInteger.valueOf(perGram).multiply(BigInteger.valueOf(in.weightMg())), MICROS_PER_GRAM_MILLIGRAM);
        return new Result(perGram24k, perGram, value);
    }

    /** Whole units of the base currency for a price in micros per gram and a weight in milligrams. */
    public static long valueOf(long perGramMicros, long weightMg) {
        return halfUp(BigInteger.valueOf(perGramMicros).multiply(BigInteger.valueOf(weightMg)), MICROS_PER_GRAM_MILLIGRAM);
    }

    public static long microsToWhole(long micros) {
        return halfUp(BigInteger.valueOf(micros), MICRO);
    }

    private static long halfUp(BigInteger numerator, long divisor) {
        BigInteger d = BigInteger.valueOf(divisor);
        return numerator.add(d.shiftRight(1)).divide(d).longValueExact();
    }
}
