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
import iq.mizan.sync.service.SyncRowRepository;
import iq.mizan.sync.service.VaultFields;
import iq.mizan.sync.service.VaultRowRepository;

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
 * uploads second. Object keys are derived here, never taken from the client. The bytes are
 * ciphertext and so is the metadata, hence the generic content type.
 */
@Service
@AllArgsConstructor
public class AttachmentService {

    private static final String CIPHERTEXT_CONTENT_TYPE = "application/octet-stream";

    private final VaultRowRepository vault;
    private final SyncRowRepository legacyRows;
    private final AttachmentSyncTable table;
    private final S3Presigner presigner;
    private final StorageProperties storage;

    @Transactional(readOnly = true)
    public PresignedUrlResponse uploadUrl(CurrentUser user, UUID attachmentId) {
        requireVisible(attachmentId);
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(storage.urlTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(storage.bucket()).key(objectKey(user, attachmentId)).contentType(CIPHERTEXT_CONTENT_TYPE).build())
                .build());
        return new PresignedUrlResponse(presigned.url().toString(), "PUT", Instant.now().plus(storage.urlTtl()));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse downloadUrl(CurrentUser user, UUID attachmentId) {
        requireVisible(attachmentId);
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(storage.urlTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(storage.bucket()).key(objectKey(user, attachmentId)).build())
                .build());
        return new PresignedUrlResponse(presigned.url().toString(), "GET", Instant.now().plus(storage.urlTtl()));
    }

    /** Encrypted rows first; a household that has not migrated yet still has the plain row. */
    private void requireVisible(UUID attachmentId) {
        boolean encrypted = vault.find(attachmentId, AttachmentSyncTable.NAME)
                .filter(row -> row.fields().get(VaultFields.DELETED_AT) == null)
                .isPresent();
        boolean legacy = !encrypted && legacyRows.fetch(table, List.of(attachmentId)).stream()
                .anyMatch(row -> row.fields().get(VaultFields.DELETED_AT) == null);
        if (!encrypted && !legacy) {
            throw ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such attachment");
        }
    }

    private static String objectKey(CurrentUser user, UUID attachmentId) {
        return user.householdId() + "/" + attachmentId;
    }
}
