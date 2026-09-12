package iq.mizan.common.exception;

import org.springframework.http.HttpStatus;

/** Clients branch on the enum name; the title and detail may be reworded freely. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation Failed"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Forbidden"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"),

    // Accounts and sessions
    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, "Invalid Email Or Phone"),
    IDENTIFIER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email Or Phone Already Registered"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid Credentials"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Account Not Active"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Account Locked"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token"),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "Already Verified"),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "Invalid Verification Code"),
    VERIFICATION_ATTEMPTS_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "Verification Attempts Exhausted"),
    VERIFICATION_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Verification Code Requested Too Soon"),

    // Households
    HOUSEHOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "Household Not Found"),

    // Sync
    SYNC_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Sync Device Not Found"),
    SYNC_TABLE_UNKNOWN(HttpStatus.BAD_REQUEST, "Unknown Sync Table"),
    SYNC_FIELD_UNKNOWN(HttpStatus.BAD_REQUEST, "Unknown Sync Field"),
    SYNC_BATCH_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Sync Batch Too Large");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String code() {
        return name();
    }

    public String typeSlug() {
        return name().toLowerCase().replace('_', '-');
    }
}
