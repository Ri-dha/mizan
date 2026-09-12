package iq.mizan.auth.controller;

import jakarta.servlet.http.HttpServletRequest;

/** Context recorded on sessions and the audit trail; X-Forwarded-For is only as honest as the proxy. */
final class ClientRequest {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private ClientRequest() {
    }

    static String userAgent(HttpServletRequest request) {
        return request.getHeader(USER_AGENT_HEADER);
    }

    static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
