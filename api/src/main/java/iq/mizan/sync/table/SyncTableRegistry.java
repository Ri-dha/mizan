package iq.mizan.sync.table;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class SyncTableRegistry {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final Map<String, SyncTable> tables;

    public SyncTableRegistry(List<SyncTable> tables) {
        tables.forEach(SyncTableRegistry::validate);
        this.tables = tables.stream().collect(Collectors.toUnmodifiableMap(SyncTable::name, Function.identity()));
    }

    public SyncTable require(String name) {
        SyncTable table = tables.get(name);
        if (table == null) {
            throw ApiException.of(ErrorCode.SYNC_TABLE_UNKNOWN, "No syncable table named '%s'".formatted(name));
        }
        return table;
    }

    public static String columnFor(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String fieldFor(String column) {
        StringBuilder field = new StringBuilder();
        boolean upper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                field.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return field.toString();
    }

    // Column names reach SQL as identifiers, so nothing outside this shape is ever accepted.
    private static void validate(SyncTable table) {
        if (!SAFE_IDENTIFIER.matcher(table.name()).matches()) {
            throw new IllegalStateException("Unsafe sync table name: " + table.name());
        }
        table.fields().keySet().stream()
                .map(SyncTableRegistry::columnFor)
                .filter(column -> !SAFE_IDENTIFIER.matcher(column).matches())
                .findFirst()
                .ifPresent(column -> {
                    throw new IllegalStateException("Unsafe sync column name: " + column);
                });
    }
}
