package iq.mizan.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import iq.mizan.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class JobLockIntegrationTest extends PostgresIntegrationTest {

    private static final Duration A_MINUTE = Duration.ofMinutes(1);

    @Autowired
    private JdbcClient jdbc;

    private JobLock instanceA;
    private JobLock instanceB;
    private final AtomicInteger runs = new AtomicInteger();

    @BeforeEach
    void twoInstances() {
        jdbc.sql("delete from scheduled_job_lock").update();
        instanceA = new JobLock(jdbc);
        instanceB = new JobLock(jdbc);
    }

    @Test
    void anotherInstanceSkipsWhileTheJobRuns() {
        boolean ran = instanceA.runExclusively("job", A_MINUTE, Duration.ZERO, () -> {
            runs.incrementAndGet();
            assertThat(instanceB.runExclusively("job", A_MINUTE, Duration.ZERO, runs::incrementAndGet)).isFalse();
        });

        assertThat(ran).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void aQuickRunStillHoldsTheLockForItsMinimum() {
        instanceA.runExclusively("job", A_MINUTE, A_MINUTE, runs::incrementAndGet);

        assertThat(instanceB.runExclusively("job", A_MINUTE, A_MINUTE, runs::incrementAndGet)).isFalse();
        assertThat(runs).hasValue(1);
    }

    @Test
    void theLockIsFreeOnceItsMinimumHasPassed() {
        instanceA.runExclusively("job", A_MINUTE, Duration.ZERO, runs::incrementAndGet);

        assertThat(instanceB.runExclusively("job", A_MINUTE, Duration.ZERO, runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void aFailedRunReleasesTheLock() {
        try {
            instanceA.runExclusively("job", A_MINUTE, Duration.ZERO, () -> {
                throw new IllegalStateException("feed down");
            });
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("feed down");
        }

        assertThat(instanceB.runExclusively("job", A_MINUTE, Duration.ZERO, runs::incrementAndGet)).isTrue();
    }

    @Test
    void anAbandonedLockExpiresAfterItsMaximum() {
        jdbc.sql("insert into scheduled_job_lock values ('job', now() - interval '1 second', now() - interval '1 hour', 'crashed')").update();

        assertThat(instanceB.runExclusively("job", A_MINUTE, Duration.ZERO, runs::incrementAndGet)).isTrue();
    }

    @Test
    void differentJobsDoNotBlockEachOther() {
        instanceA.runExclusively("purge", A_MINUTE, A_MINUTE, runs::incrementAndGet);

        assertThat(instanceB.runExclusively("poll", A_MINUTE, A_MINUTE, runs::incrementAndGet)).isTrue();
    }
}
