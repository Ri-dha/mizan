package iq.mizan.household;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.household")
public record HouseholdProperties(String defaultCurrency, int defaultMonthStartDay, Invitation invitation, Deletion deletion, Advisor advisor) {

    /** §4.2 P4: how long an advisor keeps access unless the invitation says otherwise. */
    public record Advisor(int defaultAccessDays) {
    }

    public record Invitation(Duration ttl) {
    }

    /** FR-ACC-07: the household is purged this long after the owner confirms, unless withdrawn. */
    public record Deletion(Duration gracePeriod, String purgeCron) {
    }
}
