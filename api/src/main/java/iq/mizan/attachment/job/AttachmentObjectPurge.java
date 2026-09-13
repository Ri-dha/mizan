package iq.mizan.attachment.job;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import iq.mizan.common.storage.StorageProperties;
import iq.mizan.sync.SyncProperties;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/**
 * BR-15 for bytes: the objects behind attachment rows (receipts and backups) that the recycle-bin
 * purge is about to drop are removed from storage first, so nothing is left unreferenced.
 */
@Component
@AllArgsConstructor
public class AttachmentObjectPurge {

    private static final Logger log = LoggerFactory.getLogger(AttachmentObjectPurge.class);
    private static final int BATCH = 500;

    private final JdbcClient jdbc;
    private final S3Client s3;
    private final StorageProperties storage;
    private final SyncProperties sync;
    private final Clock clock;

    @Scheduled(cron = "${mizan.storage.object-purge-cron}")
    public void scheduled() {
        purge();
    }

    @Transactional
    public int purge() {
        jdbc.sql("select set_config('app.system', 'on', true)").query().singleRow();
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(sync.recycleBinRetention()).withOffsetSameInstant(ZoneOffset.UTC);
        List<Map<String, Object>> rows = jdbc.sql("select id, household_id from attachment where deleted_at < ? limit ?")
                .param(cutoff).param(BATCH).query().listOfRows();
        if (rows.isEmpty()) {
            return 0;
        }
        List<ObjectIdentifier> keys = rows.stream()
                .map(row -> ObjectIdentifier.builder().key(row.get("household_id") + "/" + row.get("id")).build())
                .toList();
        try {
            s3.deleteObjects(DeleteObjectsRequest.builder().bucket(storage.bucket()).delete(Delete.builder().objects(keys).build()).build());
            log.info("Removed {} attachment objects deleted before {}", keys.size(), cutoff);
        } catch (RuntimeException e) {
            log.warn("Could not remove {} attachment objects: {}", keys.size(), e.getMessage());
        }
        return keys.size();
    }
}
