package iq.mizan.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Every change an operator makes to someone else's account or to a price is logged with why. */
public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {
}
