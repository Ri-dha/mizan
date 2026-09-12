package iq.mizan.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public static ApiException of(ErrorCode errorCode, String detail) {
        return new ApiException(errorCode, detail);
    }

    public ApiException property(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
