package iq.mizan.auth.dto;

/** 32 bytes as unpadded base64url: what HKDF yields for the auth key on the device. */
public final class AuthKeyPattern {

    public static final String REGEX = "^[A-Za-z0-9_-]{43}$";

    private AuthKeyPattern() {
    }
}
