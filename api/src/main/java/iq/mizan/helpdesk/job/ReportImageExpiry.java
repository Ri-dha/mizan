package iq.mizan.helpdesk.job;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import iq.mizan.common.jobs.JobLock;
import iq.mizan.common.storage.StorageProperties;
import iq.mizan.helpdesk.service.SupportProperties;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/**
 * T-report-image-retention: photos of a report resolved longer ago than the retention are deleted
 * from storage. Only photos sent before the cutoff go, so a report reopened since keeps its newer
 * ones. The rows stay, marked removed, so the thread can say so.
 */
@Component
@AllArgsConstructor
public class ReportImageExpiry {

    private static final Logger log = LoggerFactory.getLogger(ReportImageExpiry.class);
    /** The most keys one DeleteObjects request accepts. */
    private static final int DELETE_BATCH = 1000;
    private static final Duration RUNS_AT_MOST = Duration.ofHours(1);
    private static final Duration HELD_AT_LEAST = Duration.ofMinutes(5);

    private final S3Client s3;
    private final StorageProperties storage;
    private final SupportProperties support;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final JobLock jobLock;

    @Scheduled(cron = "${mizan.support.image-expiry-cron}")
    public void scheduled() {
        jobLock.runExclusively("report-image-expiry", RUNS_AT_MOST, HELD_AT_LEAST, this::expire);
    }

    private record Expired(UUID reportId, UUID attachmentId) {
        String key() {
            return "support/" + reportId + "/" + attachmentId;
        }
    }

    public int expire() {
        Instant now = clock.instant();
        Timestamp cutoff = Timestamp.from(now.minus(support.imageRetention()));
        List<Expired> expired = jdbc.sql("""
                select a.report_id, a.id from support_attachment a join support_report r on r.id = a.report_id
                where r.status = 'RESOLVED' and r.last_message_at < ? and a.created_at < ? and a.removed_at is null""")
                .param(cutoff).param(cutoff)
                .query((rs, n) -> new Expired(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
        for (int from = 0; from < expired.size(); from += DELETE_BATCH) {
            List<Expired> batch = expired.subList(from, Math.min(from + DELETE_BATCH, expired.size()));
            s3.deleteObjects(DeleteObjectsRequest.builder().bucket(storage.bucket())
                    .delete(Delete.builder().objects(batch.stream().map(e -> ObjectIdentifier.builder().key(e.key()).build()).toList()).quiet(true).build())
                    .build());
            jdbc.sql("update support_attachment set removed_at = :now where id in (:ids)")
                    .param("now", Timestamp.from(now))
                    .param("ids", batch.stream().map(Expired::attachmentId).toList())
                    .update();
        }
        if (!expired.isEmpty()) {
            log.info("Removed {} problem-report photos resolved before {}", expired.size(), cutoff.toInstant());
        }
        return expired.size();
    }
}
