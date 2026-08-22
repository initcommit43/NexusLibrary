package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.security.EncryptedStringConverter;
import dev.nexus.core.security.EncryptionProperties;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptedStringConverterTest {

    private static final String KEY = Base64.getEncoder().encodeToString("nexus-unit-test-key-32-bytes!!!!".getBytes());
    private static final String TOKEN = "oauth-access-token-value";

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        converter.setProperties(new EncryptionProperties(KEY));
    }

    @Test
    void roundTripsAValue() {
        String stored = converter.convertToDatabaseColumn(TOKEN);

        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(TOKEN);
    }

    @Test
    void whatLandsInTheColumnDoesNotContainThePlaintext() {
        String stored = converter.convertToDatabaseColumn(TOKEN);

        assertThat(stored).doesNotContain(TOKEN);
    }

    /** A repeated IV under one key is the classic way GCM is broken; each write must differ. */
    @Test
    void encryptingTheSameValueTwiceProducesDifferentCiphertext() {
        String first = converter.convertToDatabaseColumn(TOKEN);
        String second = converter.convertToDatabaseColumn(TOKEN);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(TOKEN);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(TOKEN);
    }

    @Test
    void passesNullThroughUntouched() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    /** GCM authenticates as well as encrypts, so an edited column must fail rather than decode. */
    @Test
    void refusesToDecryptTamperedCiphertext() {
        String stored = converter.convertToDatabaseColumn(TOKEN);
        byte[] raw = Base64.getDecoder().decode(stored);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> converter.convertToEntityAttribute(tampered));
    }

    @Test
    void refusesToDecryptWithADifferentKey() {
        String stored = converter.convertToDatabaseColumn(TOKEN);

        EncryptedStringConverter other = new EncryptedStringConverter();
        other.setProperties(new EncryptionProperties(
                Base64.getEncoder().encodeToString("a-completely-different-key-32byt".getBytes())));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> other.convertToEntityAttribute(stored));
    }

    @Test
    void rejectsAKeyThatIsNotThirtyTwoBytes() {
        EncryptionProperties tooShort =
                new EncryptionProperties(Base64.getEncoder().encodeToString("short".getBytes()));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(tooShort::secretKey);
    }
}
