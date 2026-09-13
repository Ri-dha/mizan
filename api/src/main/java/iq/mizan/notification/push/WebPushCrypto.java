package iq.mizan.notification.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 8291 message encryption with the aes128gcm content coding of RFC 8188: an ephemeral
 * P-256 key agreed with the subscription's key, HKDF into a content key and nonce, one record.
 */
public final class WebPushCrypto {

    private static final int SALT_BYTES = 16;
    private static final int RECORD_SIZE = 4096;
    private static final int KEY_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte LAST_RECORD_DELIMITER = 0x02;
    private static final byte[] INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    public static byte[] encrypt(byte[] plaintext, ECPublicKey subscriberKey, byte[] authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair ephemeral = generator.generateKeyPair();
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            return encrypt(plaintext, subscriberKey, authSecret, ephemeral, salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    static byte[] encrypt(byte[] plaintext, ECPublicKey subscriberKey, byte[] authSecret, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        byte[] senderPoint = VapidKeys.encodePoint((ECPublicKey) ephemeral.getPublic());
        byte[] subscriberPoint = VapidKeys.encodePoint(subscriberKey);
        Keys keys = deriveKeys(agree((ECPrivateKey) ephemeral.getPrivate(), subscriberKey), authSecret, subscriberPoint, senderPoint, salt);

        byte[] padded = new byte[plaintext.length + 1];
        System.arraycopy(plaintext, 0, padded, 0, plaintext.length);
        padded[plaintext.length] = LAST_RECORD_DELIMITER;
        byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, keys, padded);

        ByteBuffer body = ByteBuffer.allocate(SALT_BYTES + 4 + 1 + senderPoint.length + ciphertext.length);
        body.put(salt).putInt(RECORD_SIZE).put((byte) senderPoint.length).put(senderPoint).put(ciphertext);
        return body.array();
    }

    /** The subscriber's side, used by tests to prove a message round-trips. */
    static byte[] decrypt(byte[] body, ECPrivateKey subscriberPrivate, ECPublicKey subscriberPublic, byte[] authSecret)
            throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[SALT_BYTES];
        buffer.get(salt);
        buffer.getInt();
        byte[] senderPoint = new byte[Byte.toUnsignedInt(buffer.get())];
        buffer.get(senderPoint);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Keys keys = deriveKeys(agree(subscriberPrivate, VapidKeys.decodePoint(senderPoint)), authSecret,
                VapidKeys.encodePoint(subscriberPublic), senderPoint, salt);
        byte[] padded = aesGcm(Cipher.DECRYPT_MODE, keys, ciphertext);
        int end = padded.length - 1;
        while (end > 0 && padded[end] == 0) {
            end--;
        }
        byte[] plaintext = new byte[end];
        System.arraycopy(padded, 0, plaintext, 0, end);
        return plaintext;
    }

    private record Keys(byte[] contentKey, byte[] nonce) {
    }

    private static Keys deriveKeys(byte[] sharedSecret, byte[] authSecret, byte[] subscriberPoint, byte[] senderPoint, byte[] salt)
            throws GeneralSecurityException {
        byte[] info = ByteBuffer.allocate(INFO_PREFIX.length + subscriberPoint.length + senderPoint.length)
                .put(INFO_PREFIX).put(subscriberPoint).put(senderPoint).array();
        byte[] ikm = hkdf(authSecret, sharedSecret, info, 32);
        byte[] prk = hmac(salt, ikm);
        return new Keys(expand(prk, CEK_INFO, KEY_BYTES), expand(prk, NONCE_INFO, NONCE_BYTES));
    }

    private static byte[] agree(ECPrivateKey ours, ECPublicKey theirs) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ours);
        agreement.doPhase(theirs, true);
        return agreement.generateSecret();
    }

    private static byte[] aesGcm(int mode, Keys keys, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(keys.contentKey(), "AES"), new GCMParameterSpec(TAG_BITS, keys.nonce()));
        return cipher.doFinal(input);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        return expand(hmac(salt, ikm), info, length);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] block = hmac(prk, ByteBuffer.allocate(info.length + 1).put(info).put((byte) 1).array());
        byte[] out = new byte[length];
        System.arraycopy(block, 0, out, 0, length);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
