package iq.mizan.common.exception;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.api.problem")
public record ApiProblemProperties(String baseUri) {

    public ApiProblemProperties {
        if (baseUri != null && !baseUri.endsWith("/")) {
            baseUri = baseUri + "/";
        }
    }

    public String typeFor(ErrorCode code) {
        return baseUri + code.typeSlug();
    }
}
