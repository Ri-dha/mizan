package iq.mizan.admin.dto;

import iq.mizan.auth.dto.AuthKeyPattern;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ElevateRequest(@NotBlank @Pattern(regexp = AuthKeyPattern.REGEX) String authKey) {
}
