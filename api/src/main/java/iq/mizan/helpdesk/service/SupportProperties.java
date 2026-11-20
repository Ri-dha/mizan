package iq.mizan.helpdesk.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "mizan.support")
public record SupportProperties(int maxImages, DataSize maxImageSize, int reportsPerDay, int messagesPerDay) {
}
