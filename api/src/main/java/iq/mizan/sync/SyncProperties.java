package iq.mizan.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.sync")
public record SyncProperties(int pullPageSize, int maxPushOps, java.time.Duration recycleBinRetention, String purgeCron,
                             java.time.Duration receiptRetention, String housekeepingCron,
                             /** ADR sync-4: how long a device's wait for changes is held open. */
                             java.time.Duration waitTimeout) {
}
