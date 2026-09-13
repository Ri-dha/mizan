package iq.mizan.notification.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** P-256 keys in the raw forms the Web Push ecosystem exchanges: a 65-byte point and a 32-byte scalar. */
public final class VapidKeys {

    private static final int COORDINATE_BYTES = 32;
    private static final int UNCOMPRESSED_POINT_BYTES = 65;
    private static final byte UNCOMPRESSED_PREFIX = 0x04;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private VapidKeys() {
    }

    public record Pair(String publicKey, String privateKey) {
    }

    public static Pair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            return new Pair(encodePublic((ECPublicKey) pair.getPublic()), encodePrivate((ECPrivateKey) pair.getPrivate()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }

    public static ECParameterSpec curve() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }

    public static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[UNCOMPRESSED_POINT_BYTES];
        out[0] = UNCOMPRESSED_PREFIX;
        System.arraycopy(fixed(key.getW().getAffineX()), 0, out, 1, COORDINATE_BYTES);
        System.arraycopy(fixed(key.getW().getAffineY()), 0, out, 1 + COORDINATE_BYTES, COORDINATE_BYTES);
        return out;
    }

    public static ECPublicKey decodePoint(byte[] point) {
        if (point.length != UNCOMPRESSED_POINT_BYTES || point[0] != UNCOMPRESSED_PREFIX) {
            throw new IllegalArgumentException("Not an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 1 + COORDINATE_BYTES));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 1 + COORDINATE_BYTES, UNCOMPRESSED_POINT_BYTES));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curve()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 point", e);
        }
    }

    public static String encodePublic(ECPublicKey key) {
        return ENCODER.encodeToString(encodePoint(key));
    }

    public static ECPublicKey decodePublic(String base64url) {
        return decodePoint(DECODER.decode(base64url));
    }

    public static String encodePrivate(ECPrivateKey key) {
        return ENCODER.encodeToString(fixed(key.getS()));
    }

    public static ECPrivateKey decodePrivate(String base64url) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, DECODER.decode(base64url)), curve()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 private key", e);
        }
    }

    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[COORDINATE_BYTES];
        int start = Math.max(0, raw.length - COORDINATE_BYTES);
        System.arraycopy(raw, start, out, COORDINATE_BYTES - (raw.length - start), raw.length - start);
        return out;
    }
}
