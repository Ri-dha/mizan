package iq.mizan.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FR-NTF-05: one reminder per subject per day, however many times the job runs. */
@Entity
@Table(name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public static NotificationLog sent(UUID userId, String kind, String dedupeKey, Instant at) {
        NotificationLog log = new NotificationLog();
        log.id = UUID.randomUUID();
        log.userId = userId;
        log.kind = kind;
        log.dedupeKey = dedupeKey;
        log.sentAt = at;
        return log;
    }
}
