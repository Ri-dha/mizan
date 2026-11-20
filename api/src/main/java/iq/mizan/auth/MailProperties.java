package iq.mizan.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.mail")
public record MailProperties(String host, int port, String username, String password, String from, boolean starttls) {

    public boolean configured() {
        return host != null && !host.isBlank();
    }
}
