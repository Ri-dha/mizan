package iq.mizan.attachment.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import iq.mizan.attachment.dto.AttachmentKind;
import iq.mizan.common.jobs.JobLock;
import iq.mizan.common.storage.StorageProperties;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * ADR storage-2: a receipt photo is deleted from storage once it is older than the retention,
 * judged by when its bytes were stored. The row stays, so the transaction keeps its amounts and
 * the app can say the photo was removed. Devices drop their own copies on the same rule.
 */
@Component
@AllArgsConstructor
public class ReceiptPhotoExpiry {

    private static final Logger log = LoggerFactory.getLogger(ReceiptPhotoExpiry.class);
    /** The most keys one DeleteObjects request accepts. */
    private static final int DELETE_BATCH = 1000;
    private static final Duration RUNS_AT_MOST = Duration.ofHours(1);
    private static final Duration HELD_AT_LEAST = Duration.ofMinutes(5);

    private final S3Client s3;
    private final StorageProperties storage;
    private final Clock clock;
    private final JobLock jobLock;

    @Scheduled(cron = "${mizan.storage.receipt-expiry-cron}")
    public void scheduled() {
        jobLock.runExclusively("receipt-photo-expiry", RUNS_AT_MOST, HELD_AT_LEAST, this::expire);
    }

    public int expire() {
        Instant cutoff = clock.instant().minus(storage.receiptRetention());
        List<ObjectIdentifier> expired = s3.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(storage.bucket()).prefix(AttachmentKind.RECEIPT_PREFIX).build())
                .contents().stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(S3Object::key)
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        for (int from = 0; from < expired.size(); from += DELETE_BATCH) {
            List<ObjectIdentifier> batch = expired.subList(from, Math.min(from + DELETE_BATCH, expired.size()));
            s3.deleteObjects(DeleteObjectsRequest.builder().bucket(storage.bucket()).delete(Delete.builder().objects(batch).build()).build());
        }
        if (!expired.isEmpty()) {
            log.info("Deleted {} receipt photos stored before {}", expired.size(), cutoff);
        }
        return expired.size();
    }
}
