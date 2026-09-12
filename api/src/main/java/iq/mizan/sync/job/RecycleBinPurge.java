package iq.mizan.sync.job;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import iq.mizan.sync.SyncProperties;
import iq.mizan.sync.table.SyncTable;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-15: soft-deleted rows stay restorable for the retention window, then go for good. Runs as
 * the system, across every household; devices apply the same rule to their local copy.
 */
@Component
@AllArgsConstructor
public class RecycleBinPurge {

    private static final Logger log = LoggerFactory.getLogger(RecycleBinPurge.class);

    private final JdbcClient jdbcClient;
    private final List<SyncTable> tables;
    private final SyncProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${mizan.sync.purge-cron}")
    @Transactional
    public void purge() {
        jdbcClient.sql("select set_config('app.system', 'on', true)").query().singleRow();
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(retention()).withOffsetSameInstant(ZoneOffset.UTC);
        int total = 0;
        for (SyncTable table : tables) {
            if (!table.fields().containsKey("deletedAt")) {
                continue;
            }
            total += jdbcClient.sql("delete from " + table.name() + " where deleted_at < ?").param(cutoff).update();
        }
        if (total > 0) {
            log.info("Purged {} rows deleted before {}", total, cutoff);
        }
    }

    private Duration retention() {
        return properties.recycleBinRetention();
    }
}
