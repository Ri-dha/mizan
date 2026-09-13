package iq.mizan.market;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.market")
public record MarketProperties(
        Duration refreshInterval,
        Duration staleAfter,
        int historyDefaultDays,
        int historyMaxDays,
        Feeds feeds,
        Stub stub) {

    public record Feeds(MetalsDev metalsDev, GoldApi goldApi, OpenExchangeRates openExchangeRates, ExchangeRateApi exchangeRateApi,
                        long parallelRateIqdMicros) {
    }

    public record MetalsDev(String url, String apiKey) {
    }

    public record GoldApi(String url, String apiKey) {
    }

    public record OpenExchangeRates(String url, String appId) {
    }

    public record ExchangeRateApi(String url) {
    }

    /** Fixed prices for environments without a provider key; dev and test only. */
    public record Stub(boolean enabled, Map<String, Long> pricesMicros) {
    }
}
