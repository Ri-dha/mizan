package iq.mizan.auth.service;

import java.time.Instant;

public record TokenPair(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {
}
