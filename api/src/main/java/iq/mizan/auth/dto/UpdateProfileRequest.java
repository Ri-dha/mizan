package iq.mizan.auth.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Fields left out stay as they are. */
public record UpdateProfileRequest(
        @Nullable @Pattern(regexp = "[A-Z]{2}") String country,
        @Nullable @Size(max = 30) List<@Pattern(regexp = "[A-Z]{3}") String> trackedCurrencies) {
}
