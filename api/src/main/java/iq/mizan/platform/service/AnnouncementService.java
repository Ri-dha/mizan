package iq.mizan.platform.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.platform.dto.AnnouncementRequest;
import iq.mizan.platform.dto.AnnouncementResponse;
import iq.mizan.platform.entity.Announcement;
import iq.mizan.platform.repository.AnnouncementRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcements;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> live() {
        return announcements.findByRetractedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(a -> a.isLiveAt(clock.instant())).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> recent() {
        return announcements.findTop50ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AnnouncementResponse publish(AnnouncementRequest request, UUID actor) {
        if (request.endsAt() != null && request.startsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "The end must be after the start");
        }
        Announcement a = announcements.save(Announcement.publish(request.messageEn().trim(), request.messageAr().trim(), request.level(),
                request.startsAt(), request.endsAt(), actor, clock.instant()));
        auditService.record(AuditRecord.of(AuditAction.ANNOUNCEMENT_PUBLISHED, AuditEntityType.ANNOUNCEMENT).actorId(actor).entityId(a.getId())
                .after(Map.of("messageEn", a.getMessageEn(), "level", a.getLevel().name())).build());
        return toResponse(a);
    }

    @Transactional
    public void retract(UUID id, UUID actor) {
        Announcement a = announcements.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND, "No such announcement"));
        a.retract(clock.instant());
        auditService.record(AuditRecord.of(AuditAction.ANNOUNCEMENT_RETRACTED, AuditEntityType.ANNOUNCEMENT).actorId(actor).entityId(id).build());
    }

    private AnnouncementResponse toResponse(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getMessageEn(), a.getMessageAr(), a.getLevel().name(), a.getStartsAt(), a.getEndsAt(),
                a.getCreatedAt(), a.getRetractedAt(), a.isLiveAt(clock.instant()));
    }
}
