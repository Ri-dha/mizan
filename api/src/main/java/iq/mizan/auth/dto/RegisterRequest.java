package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 80) String displayName,
        @Pattern(regexp = "en|ar") String locale) {

    public String localeOrDefault() {
        return locale == null ? "en" : locale;
    }
}
