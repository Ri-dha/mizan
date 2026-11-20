package iq.mizan.market.service;

import iq.mizan.market.feed.IqWealthClient;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ADR obs-5: the IQWealth quota as metrics, so an alert fires when the key is refused, the provider
 * paused polling until midnight, or the day's requests run low. Read from the client on each scrape.
 */
@Component
public class IqWealthMetrics {

    private static final double UNKNOWN = Double.NaN;

    public IqWealthMetrics(IqWealthClient iqWealth, MeterRegistry registry) {
        Gauge.builder("mizan.iqwealth.remaining.requests", iqWealth,
                        client -> client.quota().remainingToday() == null ? UNKNOWN : client.quota().remainingToday())
                .description("Requests IQWealth reports left today; NaN until the first answer of the day")
                .register(registry);
        Gauge.builder("mizan.iqwealth.key.refused", iqWealth, client -> client.quota().keyRefused() ? 1 : 0)
                .description("1 while IQWealth refuses the configured key")
                .register(registry);
        Gauge.builder("mizan.iqwealth.paused", iqWealth, client -> client.quota().pausedUntilMidnight() ? 1 : 0)
                .description("1 after IQWealth answered 429 until midnight Baghdad time")
                .register(registry);
        Gauge.builder("mizan.iqwealth.configured", iqWealth, client -> client.configured() ? 1 : 0)
                .description("1 when an IQWealth key is set")
                .register(registry);
    }
}
