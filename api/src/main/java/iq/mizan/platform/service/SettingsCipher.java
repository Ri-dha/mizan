package iq.mizan.platform.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Secrets at rest in `platform_setting`: a database copy or backup alone does not reveal them. */
@Component
public class SettingsCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SettingsCipher(@Value("${mizan.platform.settings-key:}") String encodedKey) {
        byte[] raw = encodedKey == null || encodedKey.isBlank() ? null : Base64.getDecoder().decode(encodedKey.trim());
        this.key = raw != null && raw.length == KEY_BYTES ? new SecretKeySpec(raw, "AES") : null;
    }

    public boolean available() {
        return key != null;
    }

    public byte[] seal(String plain, String settingKey) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(settingKey.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a setting", e);
        }
    }

    public String open(byte[] sealed, String settingKey) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            cipher.updateAAD(settingKey.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("A stored setting does not open with MIZAN_SETTINGS_KEY", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw ApiException.of(ErrorCode.SETTINGS_KEY_MISSING, "Set MIZAN_SETTINGS_KEY (32 random bytes, base64) to store secrets");
        }
    }
}
