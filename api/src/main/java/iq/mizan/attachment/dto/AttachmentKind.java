package iq.mizan.attachment.dto;

/**
 * The one thing the server is told about an attachment's bytes (ADR storage-2): receipt photos
 * are deleted after a month, backups are kept until their owner replaces them.
 */
public enum AttachmentKind {
    RECEIPT,
    BACKUP;

    /** Receipts sit under their own prefix so expiry can list them without reading any row. */
    public static final String RECEIPT_PREFIX = "receipts/";

    public String objectKey(Object householdId, Object attachmentId) {
        String key = householdId + "/" + attachmentId;
        return this == RECEIPT ? RECEIPT_PREFIX + key : key;
    }
}
