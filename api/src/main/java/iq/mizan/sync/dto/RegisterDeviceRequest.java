package iq.mizan.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(@NotBlank @Size(max = 80) String name) {
}
