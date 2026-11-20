package iq.mizan.platform.service;

import java.util.List;

/**
 * ADR admin-1: the only values an operator may change at run time, each with its environment
 * default. Anything not listed (database, signing keys, storage, the settings key itself) stays
 * environment-only.
 */
public enum SettingKey {
    MARKET_IQWEALTH_API_KEY(Kind.SECRET, "mizan.market.feeds.iq-wealth.api-key"),
    MARKET_IQWEALTH_PARALLEL_SIDE(Kind.CHOICE, "mizan.market.feeds.iq-wealth.parallel-side", List.of("BUY", "SELL", "MID")),
    MARKET_IQWEALTH_RESERVE(Kind.INTEGER, "mizan.market.feeds.iq-wealth.reserve", 0, 99),
    MARKET_METALS_DEV_API_KEY(Kind.SECRET, "mizan.market.feeds.metals-dev.api-key"),
    MARKET_GOLD_API_KEY(Kind.SECRET, "mizan.market.feeds.gold-api.api-key"),
    MARKET_OPEN_EXCHANGE_RATES_APP_ID(Kind.SECRET, "mizan.market.feeds.open-exchange-rates.app-id"),
    /** Whole dinars per dollar; the environment holds micros, converted where it is read. */
    MARKET_MANUAL_DOLLAR_IQD(Kind.INTEGER, "mizan.market.feeds.parallel-rate-iqd-micros", 0, 100_000),
    MARKET_BAGHDAD_BULLION_ENABLED(Kind.BOOLEAN, "mizan.market.feeds.baghdad-bullion.enabled"),
    MARKET_GOLD_API_COM_ENABLED(Kind.BOOLEAN, "mizan.market.feeds.gold-api-com.enabled"),
    /** Which feed answers gold first; the others follow as fallbacks (ADR prices-3). */
    MARKET_GOLD_PRIMARY(Kind.CHOICE, "mizan.market.feeds.gold-primary", List.of("iraqsm.com", "baghdadbullionhouse.com")),
    MARKET_STALE_AFTER(Kind.DURATION, "mizan.market.stale-after"),
    MARKET_REFRESH_COOLDOWN(Kind.DURATION, "mizan.market.manual-refresh-cooldown");

    public enum Kind { SECRET, TEXT, BOOLEAN, INTEGER, DURATION, CHOICE }

    private final Kind kind;
    private final String property;
    private final List<String> choices;
    private final long min;
    private final long max;

    SettingKey(Kind kind, String property) {
        this(kind, property, List.of(), Long.MIN_VALUE, Long.MAX_VALUE);
    }

    SettingKey(Kind kind, String property, List<String> choices) {
        this(kind, property, choices, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    SettingKey(Kind kind, String property, long min, long max) {
        this(kind, property, List.of(), min, max);
    }

    SettingKey(Kind kind, String property, List<String> choices, long min, long max) {
        this.kind = kind;
        this.property = property;
        this.choices = choices;
        this.min = min;
        this.max = max;
    }

    public Kind kind() {
        return kind;
    }

    public String property() {
        return property;
    }

    public List<String> choices() {
        return choices;
    }

    public long min() {
        return min;
    }

    public long max() {
        return max;
    }
}
