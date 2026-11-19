package iq.mizan.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A phone's Expo push token, as `expo-notifications` hands it to the app (ADR push-1). */
public record RegisterPhoneRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "^Expo(nent)?PushToken\\[[A-Za-z0-9_-]+\\]$") String token) {
}
