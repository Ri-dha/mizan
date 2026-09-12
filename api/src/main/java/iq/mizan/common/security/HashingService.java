package iq.mizan.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/** Peppered HMAC for values looked up by hash: refresh tokens and verification codes. */
@Service
public class HashingService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HashingService(HashingProperties properties) {
        this.key = new SecretKeySpec(
                properties.hashPepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to compute HMAC", e);
        }
    }

    public boolean matches(String candidate, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(candidate).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
