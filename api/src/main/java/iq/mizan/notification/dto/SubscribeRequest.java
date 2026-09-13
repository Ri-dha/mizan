package iq.mizan.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The browser's PushSubscription as JSON: endpoint plus the p256dh and auth keys. */
public record SubscribeRequest(@NotBlank @Size(max = 1024) String endpoint, @NotBlank @Size(max = 128) String p256dh,
                               @NotBlank @Size(max = 64) String auth) {
}
