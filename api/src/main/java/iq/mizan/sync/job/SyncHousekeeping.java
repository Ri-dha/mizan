package iq.mizan.sync.job;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import iq.mizan.common.jobs.JobLock;
import iq.mizan.sync.SyncProperties;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR sync-2: keeps the two sync tables that only grow at the size the protocol needs. A pull
 * returns each changed record once, as it is now, so only the newest log entry per record is ever
 * needed, and none once the recycle bin has purged the record (ADR sync-5); a receipt only
 * matters while its device might still retry the push.
 */
@Component
@AllArgsConstructor
public class SyncHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(SyncHousekeeping.class);
    private static final Duration RUNS_AT_MOST = Duration.ofHours(1);
    private static final Duration HELD_AT_LEAST = Duration.ofMinutes(5);

    public record Tidied(int logEntries, int receipts) {
    }

    private final JdbcClient jdbc;
    private final SyncProperties properties;
    private final Clock clock;
    private final JobLock jobLock;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "${mizan.sync.housekeeping-cron}")
    public void scheduled() {
        jobLock.runExclusively("sync-housekeeping", RUNS_AT_MOST, HELD_AT_LEAST, this::run);
    }

    public Tidied run() {
        Tidied tidied = transactions.execute(status -> {
            jdbc.sql("select set_config('app.system', 'on', true)").query().singleRow();
            int superseded = jdbc.sql("""
                            delete from sync_log s using sync_log newer
                            where newer.table_name = s.table_name and newer.row_id = s.row_id and newer.seq > s.seq""")
                    .update();
            int purged = jdbc.sql("delete from sync_log s where not exists (select 1 from vault_record v where v.id = s.row_id)")
                    .update();
            int receipts = jdbc.sql("delete from sync_op_receipt where applied_at < ?")
                    .param(Timestamp.from(clock.instant().minus(properties.receiptRetention())))
                    .update();
            return new Tidied(superseded + purged, receipts);
        });
        if (tidied.logEntries() > 0 || tidied.receipts() > 0) {
            log.info("Removed {} superseded sync log entries and {} old push receipts", tidied.logEntries(), tidied.receipts());
        }
        return tidied;
    }
}
