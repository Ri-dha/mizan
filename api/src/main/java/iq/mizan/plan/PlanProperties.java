package iq.mizan.plan;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** FR-PLN-02: the split every household starts with. */
@ConfigurationProperties(prefix = "mizan.plan")
public record PlanProperties(List<DefaultBucket> defaultBuckets) {

    public record DefaultBucket(String name, String colour, int shareBasisPoints) {
    }
}
