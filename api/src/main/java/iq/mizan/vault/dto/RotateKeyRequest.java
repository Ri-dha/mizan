package iq.mizan.vault.dto;

import java.util.List;
import java.util.UUID;

import iq.mizan.vault.entity.KeyKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new version of one shared key, wrapped for every remaining member who may hold it. */
public record RotateKeyRequest(@NotNull KeyKind keyKind, @Min(2) int keyVersion,
                               @NotEmpty @Size(max = 200) List<@Valid Recipient> grants) {

    public record Recipient(@NotNull UUID userId, @NotBlank @Size(max = 4096) String wrappedKey) {
    }
}
