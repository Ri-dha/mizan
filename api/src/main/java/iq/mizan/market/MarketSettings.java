package iq.mizan.market;

import java.time.Duration;

import iq.mizan.platform.service.SettingKey;
import iq.mizan.platform.service.SettingsService;

import org.springframework.stereotype.Component;

/** What an operator may change about the feeds without a restart (ADR admin-1), read on each use. */
@Component
public class MarketSettings {

    private static final long MICROS = 1_000_000L;

    private final SettingsService settings;

    public MarketSettings(SettingsService settings) {
        this.settings = settings;
    }

    public String iqWealthApiKey() {
        return settings.text(SettingKey.MARKET_IQWEALTH_API_KEY);
    }

    public String iqWealthParallelSide() {
        return settings.text(SettingKey.MARKET_IQWEALTH_PARALLEL_SIDE);
    }

    public int iqWealthReserve() {
        return (int) settings.integer(SettingKey.MARKET_IQWEALTH_RESERVE);
    }

    public String metalsDevApiKey() {
        return settings.text(SettingKey.MARKET_METALS_DEV_API_KEY);
    }

    public String goldApiKey() {
        return settings.text(SettingKey.MARKET_GOLD_API_KEY);
    }

    public String openExchangeRatesAppId() {
        return settings.text(SettingKey.MARKET_OPEN_EXCHANGE_RATES_APP_ID);
    }

    public long manualDollarMicros() {
        return settings.integer(SettingKey.MARKET_MANUAL_DOLLAR_IQD) * MICROS;
    }

    public boolean baghdadBullionEnabled() {
        return settings.bool(SettingKey.MARKET_BAGHDAD_BULLION_ENABLED);
    }

    public String goldPrimary() {
        return settings.text(SettingKey.MARKET_GOLD_PRIMARY);
    }

    public boolean goldApiComEnabled() {
        return settings.bool(SettingKey.MARKET_GOLD_API_COM_ENABLED);
    }

    public Duration staleAfter() {
        return settings.duration(SettingKey.MARKET_STALE_AFTER);
    }

    public Duration refreshCooldown() {
        return settings.duration(SettingKey.MARKET_REFRESH_COOLDOWN);
    }
}
