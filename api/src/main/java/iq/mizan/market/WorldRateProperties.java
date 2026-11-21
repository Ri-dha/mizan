package iq.mizan.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T-world-rates: Frankfurter's daily rates for the world's currencies. {@code historyDays} is how
 * far back an empty table is filled; {@code cron} "-" turns the fetch off.
 */
@ConfigurationProperties(prefix = "mizan.market.world-rates")
public record WorldRateProperties(String url, String cron, String zone, int historyDays) {
}
