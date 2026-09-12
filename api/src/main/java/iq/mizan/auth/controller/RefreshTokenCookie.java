package iq.mizan.auth.controller;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import iq.mizan.common.security.JwtProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Keeps the thirty-day refresh token out of reach of page scripts. Browsers use the cookie;
 * the token is also in the body for clients without a cookie jar.
 */
@Component
@AllArgsConstructor
public class RefreshTokenCookie {

    static final String NAME = "mizan_refresh";
    private static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public void set(HttpHeaders headers, String refreshToken, boolean secure) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(refreshToken, secure)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build()
                .toString());
    }

    public void clear(HttpHeaders headers, boolean secure) {
        headers.add(HttpHeaders.SET_COOKIE, cookie("", secure).maxAge(Duration.ZERO).build().toString());
    }

    /**
     * Body first: a caller presenting a rotated-out token is how theft announces itself, and
     * preferring the cookie would hide that from the reuse detector.
     */
    public Optional<String> from(HttpServletRequest request, String fromBody) {
        return Optional.ofNullable(fromBody)
                .filter(token -> !token.isBlank())
                .or(() -> cookieValue(request));
    }

    public boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private static ResponseCookie.ResponseCookieBuilder cookie(String value, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(PATH)
                .sameSite("Strict");
    }

    private static Optional<String> cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
