package iq.mizan.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.storage")
public record StorageProperties(
        String bucket,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean createBucketOnStartup) {
}
