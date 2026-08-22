package dev.nexus.core.security;

import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexus.encryption")
public record EncryptionProperties(@NotBlank String key) {

    private static final int AES_256_BYTES = 32;

    public SecretKeySpec secretKey() {
        byte[] decoded = Base64.getDecoder().decode(key);
        if (decoded.length != AES_256_BYTES) {
            throw new IllegalStateException(
                    "NEXUS_ENCRYPTION_KEY must decode to 32 bytes for AES-256, got " + decoded.length);
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
