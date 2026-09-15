package iq.mizan.auth.controller;

import jakarta.servlet.http.HttpServletRequest;

/** Context recorded on sessions and the audit trail; X-Forwarded-For is only as honest as the proxy. */
final class ClientRequest {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";
    /** Matches refresh_token.user_agent; anything longer is only noise for the sessions list. */
    private static final int MAX_USER_AGENT = 1024;

    private ClientRequest() {
    }

    static String userAgent(HttpServletRequest request) {
        String agent = request.getHeader(USER_AGENT_HEADER);
        return agent != null && agent.length() > MAX_USER_AGENT ? agent.substring(0, MAX_USER_AGENT) : agent;
    }

    static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
