package iq.mizan.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribeRequest(@NotBlank String endpoint) {
}
