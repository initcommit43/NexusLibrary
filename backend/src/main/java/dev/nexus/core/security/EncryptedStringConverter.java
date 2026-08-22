package dev.nexus.core.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encrypts a column at rest with AES-GCM, so a database dump never yields usable OAuth
 * tokens on its own.
 *
 * <p>A fresh random IV per value is prepended to the ciphertext: reusing an IV under the
 * same key breaks GCM badly, leaking plaintext relationships between rows.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static SecretKeySpec key;

    private final SecureRandom random = new SecureRandom();

    /**
     * JPA instantiates converters itself, outside the container, so the key is held
     * statically and injected once at startup rather than per instance.
     */
    @Autowired
    public void setProperties(EncryptionProperties properties) {
        key = properties.secretKey();
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        requireKey();

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        requireKey();

        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            return new String(
                    cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Never log the value or the key; the message alone is enough to diagnose.
            throw new IllegalStateException("Could not decrypt value; the encryption key may have changed", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Encryption key is not configured");
        }
    }
}
