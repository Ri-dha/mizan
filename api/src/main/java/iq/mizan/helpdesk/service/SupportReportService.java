package iq.mizan.helpdesk.service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.storage.StorageProperties;
import iq.mizan.notification.service.NotificationMessages;
import iq.mizan.notification.service.NotificationService;
import iq.mizan.helpdesk.dto.ReportCategory;
import iq.mizan.helpdesk.dto.ReportDetailResponse;
import iq.mizan.helpdesk.dto.ReportStatus;
import iq.mizan.helpdesk.dto.ReportSummaryResponse;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * ADR support-1: problem and feedback reports. A person sees only their own; operators see all.
 * The text and images are stored readable, because operators have to read them.
 */
@Service
@AllArgsConstructor
public class SupportReportService {

    private static final Logger log = LoggerFactory.getLogger(SupportReportService.class);
    private static final Duration DAY = Duration.ofDays(1);
    private static final int SUBJECT_MAX = 120;
    private static final int BODY_MAX = 4000;

    public record Image(String contentType, byte[] bytes) {
    }

    private final JdbcClient jdbc;
    private final S3Client s3;
    private final StorageProperties storage;
    private final SupportProperties properties;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final NotificationMessages messages;
    private final Clock clock;

    @Transactional
    public ReportDetailResponse create(UUID userId, ReportCategory category, String subject, String body, @Nullable String appVersion,
                                       @Nullable String platform, @Nullable List<MultipartFile> images) {
        String cleanSubject = text(subject, SUBJECT_MAX, "subject");
        String cleanBody = text(body, BODY_MAX, "description");
        List<ReportImages.Clean> cleanImages = images(images);
        Instant now = clock.instant();
        requireUnder(properties.reportsPerDay(), jdbc.sql("select count(*) from support_report where user_id = ? and created_at > ?")
                .param(userId).param(Timestamp.from(now.minus(DAY))).query(Long.class).single(), "reports");
        UUID reportId = UUID.randomUUID();
        jdbc.sql("""
                insert into support_report (id, user_id, category, subject, app_version, platform, created_at, last_message_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""")
                .param(reportId).param(userId).param(category.name()).param(cleanSubject).param(clip(appVersion)).param(clip(platform))
                .param(Timestamp.from(now)).param(Timestamp.from(now)).update();
        addMessage(reportId, userId, false, cleanBody, cleanImages, now);
        return detail(reportId, false);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> mine(UUID userId) {
        return jdbc.sql("""
                select id, category, subject, status, created_at, last_message_at, unread_by_user as unread
                from support_report where user_id = ? order by last_message_at desc limit 100""")
                .param(userId).query((rs, n) -> summary(rs, null)).list();
    }

    @Transactional
    public ReportDetailResponse openForUser(UUID userId, UUID reportId) {
        ownedBy(userId, reportId);
        jdbc.sql("update support_report set unread_by_user = false where id = ?").param(reportId).update();
        return detail(reportId, false);
    }

    @Transactional
    public ReportDetailResponse replyAsUser(UUID userId, UUID reportId, @Nullable String body, @Nullable List<MultipartFile> images) {
        ownedBy(userId, reportId);
        Instant now = clock.instant();
        requireUnder(properties.messagesPerDay(), jdbc.sql("""
                select count(*) from support_message m join support_report r on r.id = m.report_id
                where r.user_id = ? and m.from_operator = false and m.created_at > ?""")
                .param(userId).param(Timestamp.from(now.minus(DAY))).query(Long.class).single(), "messages");
        addMessage(reportId, userId, false, replyText(body, images), images(images), now);
        jdbc.sql("""
                update support_report set last_message_at = ?, unread_by_operator = true,
                    status = case when status = 'RESOLVED' then 'OPEN' else status end
                where id = ?""").param(Timestamp.from(now)).param(reportId).update();
        return detail(reportId, false);
    }

    @Transactional(readOnly = true)
    public Image imageForUser(UUID userId, UUID reportId, UUID attachmentId) {
        ownedBy(userId, reportId);
        return image(reportId, attachmentId);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> queue(@Nullable ReportStatus status) {
        return jdbc.sql("""
                select r.id, r.category, r.subject, r.status, r.created_at, r.last_message_at, r.unread_by_operator as unread,
                       u.id as user_id, u.display_name, u.email
                from support_report r join app_user u on u.id = r.user_id
                where (cast(? as varchar) is null or r.status = ?)
                order by r.unread_by_operator desc, r.last_message_at desc limit 200""")
                .param(status == null ? null : status.name()).param(status == null ? null : status.name())
                .query((rs, n) -> summary(rs, new ReportSummaryResponse.ReportReporter(
                        rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("email"))))
                .list();
    }

    @Transactional
    public ReportDetailResponse openForOperator(UUID reportId) {
        exists(reportId);
        jdbc.sql("update support_report set unread_by_operator = false where id = ?").param(reportId).update();
        return detail(reportId, true);
    }

    @Transactional
    public ReportDetailResponse replyAsOperator(UUID actor, UUID reportId, @Nullable String body, @Nullable List<MultipartFile> images) {
        UUID reporter = exists(reportId);
        Instant now = clock.instant();
        addMessage(reportId, actor, true, replyText(body, images), images(images), now);
        jdbc.sql("""
                update support_report set last_message_at = ?, unread_by_user = true, unread_by_operator = false,
                    status = case when status = 'OPEN' then 'IN_PROGRESS' else status end
                where id = ?""").param(Timestamp.from(now)).param(reportId).update();
        auditService.record(AuditRecord.of(AuditAction.SUPPORT_REPLIED, AuditEntityType.SUPPORT_REPORT).actorId(actor).entityId(reportId).build());
        afterCommit(() -> notifications.notifyUser(reporter, locale -> messages.supportReply(locale, reportId)));
        return detail(reportId, true);
    }

    @Transactional
    public ReportDetailResponse setStatus(UUID actor, UUID reportId, ReportStatus status) {
        exists(reportId);
        jdbc.sql("update support_report set status = ?, unread_by_user = true where id = ?").param(status.name()).param(reportId).update();
        auditService.record(AuditRecord.of(AuditAction.SUPPORT_STATUS_CHANGED, AuditEntityType.SUPPORT_REPORT).actorId(actor).entityId(reportId)
                .after(Map.of("status", status.name())).build());
        return detail(reportId, true);
    }

    @Transactional(readOnly = true)
    public Image imageForOperator(UUID reportId, UUID attachmentId) {
        exists(reportId);
        return image(reportId, attachmentId);
    }

    @Transactional(readOnly = true)
    public long unreadForOperators() {
        return jdbc.sql("select count(*) from support_report where unread_by_operator").query(Long.class).single();
    }

    /** Before the account row goes: the rows cascade with it, the stored images do not. */
    public void deleteImagesOf(UUID userId) {
        List<ObjectIdentifier> keys = jdbc.sql("""
                select a.report_id, a.id from support_attachment a join support_report r on r.id = a.report_id where r.user_id = ?""")
                .param(userId)
                .query((rs, n) -> ObjectIdentifier.builder().key(key(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).build())
                .list();
        for (int from = 0; from < keys.size(); from += 1000) {
            s3.deleteObjects(DeleteObjectsRequest.builder().bucket(storage.bucket())
                    .delete(Delete.builder().objects(keys.subList(from, Math.min(keys.size(), from + 1000))).quiet(true).build()).build());
        }
    }

    private void addMessage(UUID reportId, UUID authorId, boolean fromOperator, String body, List<ReportImages.Clean> images, Instant now) {
        UUID messageId = UUID.randomUUID();
        jdbc.sql("insert into support_message (id, report_id, author_id, from_operator, body, created_at) values (?, ?, ?, ?, ?, ?)")
                .param(messageId).param(reportId).param(authorId).param(fromOperator).param(body).param(Timestamp.from(now)).update();
        for (int position = 0; position < images.size(); position++) {
            ReportImages.Clean image = images.get(position);
            UUID attachmentId = UUID.randomUUID();
            jdbc.sql("""
                    insert into support_attachment (id, report_id, message_id, content_type, size_bytes, position, created_at)
                    values (?, ?, ?, ?, ?, ?, ?)""")
                    .param(attachmentId).param(reportId).param(messageId).param(image.contentType()).param(image.bytes().length).param(position)
                    .param(Timestamp.from(now)).update();
            s3.putObject(PutObjectRequest.builder().bucket(storage.bucket()).key(key(reportId, attachmentId))
                    .contentType(image.contentType()).build(), RequestBody.fromBytes(image.bytes()));
        }
    }

    private ReportDetailResponse detail(UUID reportId, boolean forOperator) {
        Map<UUID, List<ReportDetailResponse.ReportAttachment>> attachments = new LinkedHashMap<>();
        jdbc.sql("select id, message_id, content_type, size_bytes, removed_at from support_attachment where report_id = ? order by created_at, position")
                .param(reportId).query(rs -> {
                    attachments.computeIfAbsent(rs.getObject("message_id", UUID.class), k -> new ArrayList<>())
                            .add(new ReportDetailResponse.ReportAttachment(rs.getObject("id", UUID.class), rs.getString("content_type"), rs.getInt("size_bytes"),
                                    rs.getTimestamp("removed_at") == null ? null : rs.getTimestamp("removed_at").toInstant()));
                });
        List<ReportDetailResponse.ReportMessage> thread = jdbc.sql("""
                select m.id, m.from_operator, m.body, m.created_at, u.display_name
                from support_message m left join app_user u on u.id = m.author_id
                where m.report_id = ? order by m.created_at, m.id""")
                .param(reportId)
                .query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    boolean fromOperator = rs.getBoolean("from_operator");
                    // People see "Mizan support", not which operator answered.
                    String author = fromOperator && !forOperator ? null : rs.getString("display_name");
                    return new ReportDetailResponse.ReportMessage(id, fromOperator, author, rs.getString("body"),
                            rs.getTimestamp("created_at").toInstant(), attachments.getOrDefault(id, List.of()));
                }).list();
        return jdbc.sql("""
                select r.id, r.category, r.subject, r.status, r.app_version, r.platform, r.created_at, u.id as user_id, u.display_name, u.email
                from support_report r join app_user u on u.id = r.user_id where r.id = ?""")
                .param(reportId)
                .query((rs, n) -> new ReportDetailResponse(rs.getObject("id", UUID.class), ReportCategory.valueOf(rs.getString("category")),
                        rs.getString("subject"), ReportStatus.valueOf(rs.getString("status")), rs.getString("app_version"), rs.getString("platform"),
                        rs.getTimestamp("created_at").toInstant(), thread,
                        forOperator ? new ReportSummaryResponse.ReportReporter(rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                                rs.getString("email")) : null))
                .single();
    }

    private Image image(UUID reportId, UUID attachmentId) {
        record Stored(String contentType, boolean removed) {
        }
        Stored stored = jdbc.sql("select content_type, removed_at is not null as removed from support_attachment where id = ? and report_id = ?")
                .param(attachmentId).param(reportId).query((rs, n) -> new Stored(rs.getString(1), rs.getBoolean(2))).optional()
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such image"));
        if (stored.removed()) {
            throw ApiException.of(ErrorCode.IMAGE_REMOVED, "This photo was removed some months after the report was resolved");
        }
        String contentType = stored.contentType();
        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(storage.bucket()).key(key(reportId, attachmentId)).build())
                .asByteArray();
        return new Image(contentType, bytes);
    }

    private List<ReportImages.Clean> images(@Nullable List<MultipartFile> files) {
        List<MultipartFile> present = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (present.size() > properties.maxImages()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Attach at most %d images".formatted(properties.maxImages()));
        }
        List<ReportImages.Clean> clean = new ArrayList<>();
        for (MultipartFile file : present) {
            if (file.getSize() > properties.maxImageSize().toBytes()) {
                throw ApiException.of(ErrorCode.UPLOAD_TOO_LARGE, "Each image can be at most %d MB".formatted(properties.maxImageSize().toMegabytes()));
            }
            try {
                clean.add(ReportImages.clean(file.getBytes()));
            } catch (IOException e) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED, "The image could not be read");
            }
        }
        return clean;
    }

    private static String replyText(@Nullable String body, @Nullable List<MultipartFile> images) {
        boolean hasImages = images != null && images.stream().anyMatch(f -> f != null && !f.isEmpty());
        if ((body == null || body.isBlank()) && hasImages) {
            return "";
        }
        return text(body, BODY_MAX, "message");
    }

    private static String text(@Nullable String value, int max, String what) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Write a " + what);
        }
        if (trimmed.length() > max) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "The %s can be at most %d characters".formatted(what, max));
        }
        return trimmed;
    }

    private static @Nullable String clip(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
    }

    private static void requireUnder(int limit, long used, String what) {
        if (used >= limit) {
            throw ApiException.of(ErrorCode.RATE_LIMIT_EXCEEDED, "You have sent the most %s allowed in a day; try again tomorrow".formatted(what));
        }
    }

    private void ownedBy(UUID userId, UUID reportId) {
        boolean owned = jdbc.sql("select count(*) from support_report where id = ? and user_id = ?").param(reportId).param(userId)
                .query(Long.class).single() > 0;
        if (!owned) {
            throw ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such report");
        }
    }

    private UUID exists(UUID reportId) {
        return jdbc.sql("select user_id from support_report where id = ?").param(reportId).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such report"));
    }

    private static ReportSummaryResponse summary(java.sql.ResultSet rs, ReportSummaryResponse.@Nullable ReportReporter reporter) throws java.sql.SQLException {
        return new ReportSummaryResponse(rs.getObject("id", UUID.class), ReportCategory.valueOf(rs.getString("category")), rs.getString("subject"),
                ReportStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_message_at").toInstant(), rs.getBoolean("unread"), reporter);
    }

    private static String key(UUID reportId, UUID attachmentId) {
        return "support/" + reportId + "/" + attachmentId;
    }

    private static void afterCommit(Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Could not notify about a support reply", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }
}
