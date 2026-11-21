package iq.mizan.helpdesk.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "mizan.support")
/** {@code imageRetention}: how long after a report is resolved its photos are kept (T-report-image-retention). */
public record SupportProperties(int maxImages, DataSize maxImageSize, int reportsPerDay, int messagesPerDay, Duration imageRetention) {
}
