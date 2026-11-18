package iq.mizan.common.jobs;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * ADR csp-and-job-lock-1: a scheduled job runs on one instance at a time. The database's clock
 * decides, so instance clocks do not matter, and nothing is held open while the job runs.
 */
@Component
public class JobLock {

    private static final Logger log = LoggerFactory.getLogger(JobLock.class);

    private final JdbcClient jdbc;
    private final String instance = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();

    public JobLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs {@code task} only if no instance holds {@code name}. {@code atMostFor} frees the lock if
     * this instance dies mid-run; {@code atLeastFor} keeps it after a quick run, so an instance
     * whose schedule fires a moment later skips instead of repeating the work.
     */
    public boolean runExclusively(String name, Duration atMostFor, Duration atLeastFor, Runnable task) {
        if (!acquire(name, atMostFor)) {
            log.debug("Job {} is running or ran moments ago on another instance; skipped", name);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(name, atLeastFor);
        }
    }

    private boolean acquire(String name, Duration atMostFor) {
        return jdbc.sql("""
                        insert into scheduled_job_lock (name, locked_until, locked_at, locked_by)
                        values (?, now() + make_interval(secs => ?), now(), ?)
                        on conflict (name) do update
                            set locked_until = excluded.locked_until, locked_at = excluded.locked_at, locked_by = excluded.locked_by
                            where scheduled_job_lock.locked_until <= now()""")
                .param(name).param(atMostFor.toSeconds()).param(instance)
                .update() == 1;
    }

    private void release(String name, Duration atLeastFor) {
        jdbc.sql("""
                        update scheduled_job_lock
                        set locked_until = greatest(now(), locked_at + make_interval(secs => ?))
                        where name = ? and locked_by = ?""")
                .param(atLeastFor.toSeconds()).param(name).param(instance)
                .update();
    }
}
