package iq.mizan.sync.service;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Stamps rows the server creates itself, in the same format devices use. Wall time first, so
 * any later edit from a device outranks a server-seeded value; a counter keeps stamps issued
 * within one millisecond distinct and increasing.
 */
@Component
public class ServerClock {

    static final String NODE = "server";
    private static final int COUNTER_BITS = 16;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;

    private final Clock clock;
    private final AtomicLong last = new AtomicLong();

    public ServerClock(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        long floor = clock.millis() << COUNTER_BITS;
        long stamp = last.updateAndGet(previous -> Math.max(previous + 1, floor));
        return "%013d:%04x:%s".formatted(stamp >> COUNTER_BITS, stamp & COUNTER_MASK, NODE);
    }
}
