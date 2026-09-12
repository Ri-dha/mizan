package iq.mizan.household;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.household")
public record HouseholdProperties(String defaultCurrency, int defaultMonthStartDay) {
}
