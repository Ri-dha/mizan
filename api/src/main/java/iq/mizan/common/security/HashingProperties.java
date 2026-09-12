package iq.mizan.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.security")
public record HashingProperties(String hashPepper) {
}
