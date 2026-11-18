package iq.mizan.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrantOperatorRequest(@NotBlank @Size(max = 254) String identifier) {
}
