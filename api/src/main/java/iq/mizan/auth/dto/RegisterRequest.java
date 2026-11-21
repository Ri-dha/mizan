package iq.mizan.auth.dto;

import org.jspecify.annotations.Nullable;
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
        /** ISO 3166 alpha-2; Iraq when left out, for app versions that do not ask. */
        @Nullable @Pattern(regexp = "[A-Z]{2}") String country,
        /** The household's currency; the country's own when left out. */
        @Nullable @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
        @NotNull @Valid AccountKeysRequest keys) {

    public String localeOrDefault() {
        return locale == null ? "en" : locale;
    }
}
