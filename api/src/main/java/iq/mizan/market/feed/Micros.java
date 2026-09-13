package iq.mizan.market.feed;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Micros {

    private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);

    private Micros() {
    }

    static long of(Object decimal) {
        return new BigDecimal(String.valueOf(decimal)).multiply(MICRO).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
