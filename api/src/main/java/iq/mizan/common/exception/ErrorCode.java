package iq.mizan.common.exception;

import org.springframework.http.HttpStatus;

/** Clients branch on the enum name; the title and detail may be reworded freely. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation Failed"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Forbidden"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User Not Found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"),

    // Accounts and sessions
    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, "Invalid Email"),
    IDENTIFIER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email Already Registered"),
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
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitation Not Found"),
    INVITATION_EXPIRED(HttpStatus.GONE, "Invitation Expired"),
    ALREADY_A_MEMBER(HttpStatus.CONFLICT, "Already A Member"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Member Not Found"),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "Owner Cannot Leave"),
    ONLY_HOUSEHOLD(HttpStatus.CONFLICT, "Cannot Leave The Only Household"),
    HOUSEHOLD_DELETION_PENDING(HttpStatus.CONFLICT, "Household Deletion Pending"),

    // Sync
    SYNC_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Sync Device Not Found"),
    SYNC_TABLE_UNKNOWN(HttpStatus.BAD_REQUEST, "Unknown Sync Table"),
    SYNC_FIELD_UNKNOWN(HttpStatus.BAD_REQUEST, "Unknown Sync Field"),
    SYNC_RECORD_IMMUTABLE(HttpStatus.CONFLICT, "Sync Record Is Immutable"),
    SYNC_TABLE_MISMATCH(HttpStatus.CONFLICT, "Record Belongs To Another Table"),
    /** ADR e2e-1: anything but ciphertext for a field other than visibility or deletedAt. */
    SYNC_PLAIN_VALUE_REFUSED(HttpStatus.BAD_REQUEST, "Plain Value Refused"),
    SYNC_SHARED_WRITE_REFUSED(HttpStatus.FORBIDDEN, "Shared Write Refused"),

    // Keys
    ACCOUNT_KEYS_EXIST(HttpStatus.CONFLICT, "Account Keys Already Exist"),
    ACCOUNT_KEYS_MISSING(HttpStatus.NOT_FOUND, "Account Keys Missing"),
    KEY_GRANT_REFUSED(HttpStatus.FORBIDDEN, "Key Grant Refused"),
    KEY_ROTATION_REFUSED(HttpStatus.FORBIDDEN, "Key Rotation Refused"),
    KEY_ROTATION_CONFLICT(HttpStatus.CONFLICT, "Key Rotation Conflict"),
    KEY_ROTATED(HttpStatus.CONFLICT, "Key Rotated"),
    RESET_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "Reset Not Confirmed"),
    OPERATOR_REQUIRED(HttpStatus.FORBIDDEN, "Operator Required"),
    REAUTHENTICATION_REQUIRED(HttpStatus.FORBIDDEN, "Re-authentication Required"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account Suspended"),
    SETTING_INVALID(HttpStatus.BAD_REQUEST, "Setting Invalid"),
    SETTINGS_KEY_MISSING(HttpStatus.CONFLICT, "Settings Key Missing"),
    LAST_OPERATOR(HttpStatus.CONFLICT, "Last Operator"),
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
