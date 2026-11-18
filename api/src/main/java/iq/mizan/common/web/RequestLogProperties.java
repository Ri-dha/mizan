package iq.mizan.common.web;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Which requests earn an INFO access line; the rest are DEBUG, since devices sync every few minutes. */
@ConfigurationProperties(prefix = "mizan.logging.requests")
public record RequestLogProperties(Duration slowAfter) {
}
