package iq.mizan.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.storage")
public record StorageProperties(
        String bucket,
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean createBucketOnStartup,
        java.time.Duration urlTtl,
        java.time.Duration receiptRetention) {

    /** ADR storage-4: links go to browsers and phones, which cannot reach the internal address. */
    public String linkEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
