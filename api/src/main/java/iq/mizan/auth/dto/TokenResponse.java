package iq.mizan.auth.dto;

import java.time.Instant;

import iq.mizan.auth.service.TokenPair;

public record TokenResponse(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.accessTokenExpiresAt());
    }
}
