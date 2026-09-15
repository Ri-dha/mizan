package iq.mizan.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request on every log line written while it runs (httpMethod, httpPath) and writes
 * one access line when it ends with the status and duration, so Kibana can chart latency and
 * errors per path without any other instrumentation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("iq.mizan.http");
    private static final String METHOD = "httpMethod";
    private static final String PATH = "httpPath";
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int SERVER_ERROR = 500;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        MDC.put(METHOD, request.getMethod());
        MDC.put(PATH, request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - started) / NANOS_PER_MILLI;
            int status = response.getStatus();
            var line = (status >= SERVER_ERROR ? log.atError() : log.atInfo())
                    .addKeyValue("httpStatus", status)
                    .addKeyValue("durationMs", millis)
                    .addKeyValue("clientIp", ClientAddress.of(request));
            line.log("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, millis);
            MDC.remove(METHOD);
            MDC.remove(PATH);
        }
    }
}
