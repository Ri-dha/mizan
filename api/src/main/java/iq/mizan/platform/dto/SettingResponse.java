package iq.mizan.platform.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Secrets come back masked; {@code overridden} means a database value replaces the environment default. */
public record SettingResponse(String key, String kind, String value, String defaultValue, boolean overridden,
                              List<String> choices, @Nullable Long min, @Nullable Long max, @Nullable UUID updatedBy, @Nullable Instant updatedAt) {
}
