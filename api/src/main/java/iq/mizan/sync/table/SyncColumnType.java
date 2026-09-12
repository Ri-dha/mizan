package iq.mizan.sync.table;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The handful of column shapes a syncable table may use. Each knows how to turn a wire value
 * into a JDBC parameter and how to normalise either side into one comparable form.
 */
public enum SyncColumnType {

    TEXT {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : String.valueOf(wire);
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : String.valueOf(value);
        }
    },

    INTEGER {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : Math.toIntExact(toLong(wire));
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : toLong(value);
        }
    },

    BIGINT {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : toLong(wire);
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : toLong(value);
        }
    },

    BOOLEAN {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : toBoolean(wire);
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : toBoolean(value);
        }
    },

    UUID_VALUE {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : UUID.fromString(String.valueOf(wire));
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : String.valueOf(value);
        }
    },

    TIMESTAMP {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : toInstant(wire).atOffset(ZoneOffset.UTC);
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            return value == null ? null : toInstant(value).toString();
        }
    },

    DATE {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : LocalDate.parse(String.valueOf(wire));
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            if (value == null) {
                return null;
            }
            return value instanceof java.sql.Date date ? date.toLocalDate().toString() : String.valueOf(value);
        }
    },

    JSON {
        @Override
        public Object toJdbc(Object wire, JsonMapper mapper) {
            return wire == null ? null : mapper.writeValueAsString(wire);
        }

        @Override
        public String placeholder() {
            return "?::jsonb";
        }

        @Override
        public String selectExpression(String column) {
            return column + "::text as " + column;
        }

        @Override
        public Object normalize(Object value, JsonMapper mapper) {
            if (value == null) {
                return null;
            }
            if (value instanceof JsonNode node) {
                return node;
            }
            if (value instanceof String text) {
                return mapper.readTree(text);
            }
            return mapper.valueToTree(value);
        }
    };

    public abstract Object toJdbc(Object wire, JsonMapper mapper);

    public abstract Object normalize(Object value, JsonMapper mapper);

    public String placeholder() {
        return "?";
    }

    /** JSON columns are read as text so the driver's own wrapper type never reaches the service. */
    public String selectExpression(String column) {
        return column;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                double d = number.doubleValue();
                if (d != Math.rint(d)) {
                    throw notAWholeNumber(value);
                }
            }
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw notAWholeNumber(value);
        }
    }

    private static boolean toBoolean(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime odt -> odt.toInstant();
            case Timestamp ts -> ts.toInstant();
            default -> Instant.parse(String.valueOf(value));
        };
    }

    private static ApiException notAWholeNumber(Object value) {
        return ApiException.of(ErrorCode.VALIDATION_FAILED,
                "'%s' is not a whole number; money is stored in minor units (BR-01)".formatted(value));
    }
}
