package iq.mizan.attachment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import iq.mizan.attachment.dto.PresignedUrlResponse;
import iq.mizan.attachment.sync.AttachmentSyncTable;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.storage.StorageProperties;
import iq.mizan.sync.service.StoredRow;
import iq.mizan.sync.service.SyncRowRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Hands out short-lived URLs for the bytes behind a synced attachment row. The row must already
 * be visible to the caller (row-level security decides), so a device syncs metadata first and
 * uploads second. Object keys are derived here, never taken from the client.
 */
@Service
@AllArgsConstructor
public class AttachmentService {

    private final SyncRowRepository rows;
    private final AttachmentSyncTable table;
    private final S3Presigner presigner;
    private final StorageProperties storage;

    @Transactional(readOnly = true)
    public PresignedUrlResponse uploadUrl(CurrentUser user, UUID attachmentId) {
        StoredRow row = visible(attachmentId);
        String key = objectKey(user, row.id());
        String mimeType = String.valueOf(row.fields().get("mimeType"));
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(storage.urlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(storage.bucket()).key(key).contentType(mimeType).build())
                .build());
        return new PresignedUrlResponse(presigned.url().toString(), "PUT", Instant.now().plus(storage.urlTtl()));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse downloadUrl(CurrentUser user, UUID attachmentId) {
        StoredRow row = visible(attachmentId);
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(storage.urlTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(storage.bucket()).key(objectKey(user, row.id())).build())
                .build());
        return new PresignedUrlResponse(presigned.url().toString(), "GET", Instant.now().plus(storage.urlTtl()));
    }

    private StoredRow visible(UUID attachmentId) {
        return rows.fetch(table, List.of(attachmentId)).stream()
                .filter(row -> row.fields().get("deletedAt") == null)
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such attachment"));
    }

    private static String objectKey(CurrentUser user, UUID attachmentId) {
        return user.householdId() + "/" + attachmentId;
    }
}
