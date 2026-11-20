package iq.mizan.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.test")
public record TestAccounts(
        String ownerEmail,
        String ownerPassword,
        String otherEmail,
        String otherPassword,
        String thirdEmail,
        String thirdPassword) {
}
