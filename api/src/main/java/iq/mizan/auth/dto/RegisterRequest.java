package iq.mizan.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code authKey} is derived from the password on the device; the password itself never travels. */
public record RegisterRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Pattern(regexp = AuthKeyPattern.REGEX) String authKey,
        @NotBlank @Size(max = 80) String displayName,
        @Pattern(regexp = "en|ar") String locale,
        @NotNull @Valid AccountKeysRequest keys) {

    public String localeOrDefault() {
        return locale == null ? "en" : locale;
    }
}
