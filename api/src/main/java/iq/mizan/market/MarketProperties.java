package iq.mizan.market;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mizan.market")
public record MarketProperties(
        boolean autoRefresh,
        Duration refreshInterval,
        Duration staleAfter,
        int historyDefaultDays,
        int historyMaxDays,
        /** A user-triggered refresh is honoured at most this often (FR-MKT-01). */
        Duration manualRefreshCooldown,
        Feeds feeds,
        Stub stub) {

    public record Feeds(MetalsDev metalsDev, GoldApi goldApi, OpenExchangeRates openExchangeRates, ExchangeRateApi exchangeRateApi,
                        long parallelRateIqdMicros, LocalDealer localDealer, GoldApiCom goldApiCom, BaghdadBullion baghdadBullion) {
    }

    /** The Baghdad dealer whose bid and ask per gram are the default local source. */
    public record BaghdadBullion(String url, boolean enabled) {
    }

    /** Keyless spot source; on by default so a fresh install shows live prices. */
    public record GoldApiCom(String url, boolean enabled) {
    }

    /** A JSON endpoint with the local market's prices; paths are dot-separated, units name a weight of a purity. */
    public record LocalDealer(String url, String goldPath, String goldUnit, String silverPath, String silverUnit) {
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
