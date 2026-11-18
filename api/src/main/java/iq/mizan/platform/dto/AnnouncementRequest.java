package iq.mizan.platform.dto;

import java.time.Instant;

import iq.mizan.platform.entity.Announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Both languages are required: Arabic is a first-class locale. */
public record AnnouncementRequest(@NotBlank @Size(max = 500) String messageEn, @NotBlank @Size(max = 500) String messageAr,
                                  @NotNull Announcement.Level level, Instant startsAt, Instant endsAt) {
}
