package iq.mizan.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** The caller's address as the reverse proxy reports it, falling back to the socket peer. */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
