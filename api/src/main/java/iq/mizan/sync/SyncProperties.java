package iq.mizan.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.sync")
public record SyncProperties(int pullPageSize, int maxPushOps, java.time.Duration recycleBinRetention, String purgeCron) {
}
