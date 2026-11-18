package iq.mizan.vault.dto;

import java.util.UUID;

import iq.mizan.vault.entity.KeyKind;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GrantRequest(@NotNull UUID userId, @NotNull KeyKind keyKind, @Min(1) int keyVersion,
                           @NotBlank @Size(max = 4096) String wrappedKey) {
}
