package iq.mizan.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SettingUpdateRequest(@NotNull @Size(max = 512) String value) {
}
