package iq.mizan.notification.dto;

import java.time.Instant;
import java.util.List;

import iq.mizan.notification.service.ReminderKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The device's reminders for the coming days, replacing its previous list. A dedupe key is an
 * opaque string the device chooses (a record id and a date); it must not contain names or amounts.
 */
public record ScheduleRequest(
        @NotNull @Size(max = 200) List<@Valid Item> items,
        boolean metalPriceAlerts,
        @Min(10) @Max(5000) Integer metalMoveBasisPoints) {

    public record Item(@NotNull ReminderKind kind, @NotNull Instant fireAt,
                       @NotBlank @Size(max = 160) @Pattern(regexp = "^[A-Za-z0-9:_.-]+$") String dedupeKey) {
    }
}
