package iq.mizan.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** A user's refresh and the poller's can land at once on an empty table; both must succeed and leave one row each. */
class PriceRecorderConcurrencyTest extends PostgresIntegrationTest {

    private static final Instrument GOLD = Instrument.XAU;
    private static final int WRITERS = 8;

    @Autowired
    private PriceRecorder recorder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyPriceTables() {
        jdbc.execute("delete from price_tick; delete from price_history; delete from price_quote");
    }

    @Test
    void simultaneousFirstPricesAllSucceedAndRecordOnce() throws Exception {
        FetchedPrice price = new FetchedPrice(2_650_000_000L, Instant.parse("2026-11-19T08:00:00Z"));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(WRITERS)) {
            for (int i = 0; i < WRITERS; i++) {
                writes.add(pool.submit(() -> {
                    start.await();
                    recorder.store(GOLD, "test-feed", price);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> write : writes) {
                write.get();
            }
        }

        assertThat(count("price_quote")).isEqualTo(1);
        assertThat(count("price_history")).isEqualTo(1);
        assertThat(count("price_tick")).isEqualTo(1);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where instrument = ?", Integer.class, GOLD.name());
    }
}
