package iq.mizan.platform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "announcement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {

    public enum Level { INFO, WARNING }

    @Id
    private UUID id;

    @Column(name = "message_en", nullable = false)
    private String messageEn;

    @Column(name = "message_ar", nullable = false)
    private String messageAr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "retracted_at")
    private Instant retractedAt;

    public static Announcement publish(String messageEn, String messageAr, Level level, Instant startsAt, Instant endsAt, UUID by, Instant now) {
        Announcement a = new Announcement();
        a.id = UUID.randomUUID();
        a.messageEn = messageEn;
        a.messageAr = messageAr;
        a.level = level;
        a.startsAt = startsAt == null ? now : startsAt;
        a.endsAt = endsAt;
        a.createdBy = by;
        a.createdAt = now;
        return a;
    }

    public void retract(Instant at) {
        retractedAt = at;
    }

    public boolean isLiveAt(Instant now) {
        return retractedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }
}
