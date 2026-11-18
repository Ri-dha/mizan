package iq.mizan.platform.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.platform.dto.SettingResponse;
import iq.mizan.platform.entity.PlatformSetting;
import iq.mizan.platform.repository.PlatformSettingRepository;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR admin-1: what feeds and jobs read at run time. An operator's value wins over the
 * environment; each instance re-reads the table at most every {@link #CACHE_TTL}, so a change
 * reaches every instance within that time without a restart.
 */
@Service
public class SettingsService {

    static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final long MICROS = 1_000_000L;
    private static final int MASK_VISIBLE = 4;

    private record Override(String value, UUID by, Instant at) {
    }

    private final PlatformSettingRepository settings;
    private final SettingsCipher cipher;
    private final Environment environment;
    private final AuditService auditService;
    private final Clock clock;
    private volatile Map<SettingKey, Override> overrides = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public SettingsService(PlatformSettingRepository settings, SettingsCipher cipher, Environment environment,
                           AuditService auditService, Clock clock) {
        this.settings = settings;
        this.cipher = cipher;
        this.environment = environment;
        this.auditService = auditService;
        this.clock = clock;
    }

    public String text(SettingKey key) {
        Override value = current().get(key);
        return value != null ? value.value() : defaultValue(key);
    }

    public boolean bool(SettingKey key) {
        return Boolean.parseBoolean(text(key));
    }

    public long integer(SettingKey key) {
        String value = text(key);
        return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim());
    }

    public Duration duration(SettingKey key) {
        return DurationStyle.detectAndParse(text(key));
    }

    private String defaultValue(SettingKey key) {
        String raw = environment.getProperty(key.property(), "");
        if (key == SettingKey.MARKET_MANUAL_DOLLAR_IQD) {
            return raw.isBlank() ? "0" : String.valueOf(Long.parseLong(raw.trim()) / MICROS);
        }
        return raw;
    }

    private Map<SettingKey, Override> current() {
        if (loadedAt.plus(CACHE_TTL).isAfter(clock.instant())) {
            return overrides;
        }
        Map<SettingKey, Override> fresh = new EnumMap<>(SettingKey.class);
        for (PlatformSetting row : settings.findAll()) {
            SettingKey key = known(row.getKey());
            if (key == null) {
                continue;
            }
            String value = row.getSecretValue() != null ? cipher.open(row.getSecretValue(), row.getKey()) : row.getTextValue();
            fresh.put(key, new Override(value, row.getUpdatedBy(), row.getUpdatedAt()));
        }
        overrides = fresh;
        loadedAt = clock.instant();
        return fresh;
    }

    private static SettingKey known(String name) {
        return Arrays.stream(SettingKey.values()).filter(k -> k.name().equals(name)).findFirst().orElse(null);
    }

    public List<SettingResponse> list() {
        Map<SettingKey, Override> values = current();
        return Arrays.stream(SettingKey.values()).map(key -> {
            Override value = values.get(key);
            boolean secret = key.kind() == SettingKey.Kind.SECRET;
            String effective = value != null ? value.value() : defaultValue(key);
            return new SettingResponse(key.name(), key.kind().name(), secret ? mask(effective) : effective,
                    secret ? mask(defaultValue(key)) : defaultValue(key), value != null, key.choices(),
                    key.min() == Long.MIN_VALUE ? null : key.min(), key.max() == Long.MAX_VALUE ? null : key.max(),
                    value == null ? null : value.by(), value == null ? null : value.at());
        }).toList();
    }

    @Transactional
    public void put(SettingKey key, String value, UUID actor) {
        String normalised = validate(key, value);
        boolean secret = key.kind() == SettingKey.Kind.SECRET;
        PlatformSetting row = settings.findById(key.name()).orElseGet(() -> PlatformSetting.of(key.name()));
        if (secret) {
            row.setSecret(cipher.seal(normalised, key.name()), actor, clock.instant());
        } else {
            row.setText(normalised, actor, clock.instant());
        }
        settings.save(row);
        loadedAt = Instant.EPOCH;
        auditService.record(AuditRecord.of(AuditAction.SETTING_CHANGED, AuditEntityType.PLATFORM_SETTING).actorId(actor)
                .after(Map.of("key", key.name(), "value", secret ? mask(normalised) : normalised)).build());
    }

    @Transactional
    public void reset(SettingKey key, UUID actor) {
        settings.deleteById(key.name());
        loadedAt = Instant.EPOCH;
        auditService.record(AuditRecord.of(AuditAction.SETTING_RESET, AuditEntityType.PLATFORM_SETTING).actorId(actor)
                .after(Map.of("key", key.name())).build());
    }

    private static String validate(SettingKey key, String raw) {
        String value = raw == null ? "" : raw.trim();
        try {
            switch (key.kind()) {
                case SECRET, TEXT -> require(!value.isEmpty() && value.length() <= 512, "a value up to 512 characters");
                case BOOLEAN -> require(value.equals("true") || value.equals("false"), "true or false");
                case INTEGER -> {
                    long number = Long.parseLong(value);
                    require(number >= key.min() && number <= key.max(), "a whole number from %d to %d".formatted(key.min(), key.max()));
                }
                case DURATION -> require(!DurationStyle.detectAndParse(value).isNegative(), "a duration such as 30s, 15m or 24h");
                case CHOICE -> require(key.choices().contains(value), "one of " + String.join(", ", key.choices()));
            }
        } catch (IllegalArgumentException e) {
            throw invalid(key, e.getMessage());
        }
        return value;
    }

    private static void require(boolean ok, String expected) {
        if (!ok) {
            throw new IllegalArgumentException("Expected " + expected);
        }
    }

    private static ApiException invalid(SettingKey key, String detail) {
        return ApiException.of(ErrorCode.SETTING_INVALID, "%s: %s".formatted(key.name(), detail));
    }

    static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        if (secret.length() <= MASK_VISIBLE * 2) {
            return "••••";
        }
        return secret.substring(0, MASK_VISIBLE) + "…" + secret.substring(secret.length() - 3);
    }
}
