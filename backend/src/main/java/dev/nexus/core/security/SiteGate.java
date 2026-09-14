package dev.nexus.core.security;

import dev.nexus.config.NexusProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether a browser has been let past the site password, and lets it past.
 *
 * <p>What the browser keeps is an expiry and a MAC over that expiry and a digest of the current
 * password, never the password. Nothing is stored server-side, so a restart or a second
 * instance changes nothing; changing the password changes every MAC, which is how all
 * existing cookies are retired at once.
 *
 * <p>The MAC key is derived from the JWT secret rather than asking for a new one. It is already
 * required at boot and already at least 32 bytes, and rotating it already means "everyone signs
 * in again", which is the right consequence for this cookie too. The derivation keeps the two
 * uses apart: a gate cookie is never a value the JWT key signed.
 */
@Component
public class SiteGate {

    private static final Logger log = LoggerFactory.getLogger(SiteGate.class);

    public static final Duration LIFETIME = Duration.ofDays(30);

    private static final String HMAC = "HmacSHA256";

    private final String password;
    private final byte[] macKey;

    public SiteGate(SiteGateProperties properties, NexusProperties nexus) {
        this.password = blankToNull(properties.password());
        this.macKey = hmac(nexus.jwt().secret().getBytes(StandardCharsets.UTF_8), "nexus-site-gate");
        log.info("Site gate is {}", password == null ? "off" : "on");
    }

    public boolean isEnabled() {
        return password != null;
    }

    public boolean passwordMatches(String presented) {
        // Compared as digests so the comparison takes the same time whatever the lengths.
        return password != null
                && presented != null
                && MessageDigest.isEqual(sha256(presented), sha256(password));
    }

    /** A cookie value good until {@code now + LIFETIME}. */
    public String mint(Instant now) {
        long expiry = now.plus(LIFETIME).getEpochSecond();
        return expiry + "." + mac(expiry);
    }

    public boolean isValid(String cookieValue, Instant now) {
        if (password == null || cookieValue == null) {
            return false;
        }
        int dot = cookieValue.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(cookieValue.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] expected = mac(expiry).getBytes(StandardCharsets.US_ASCII);
        byte[] presented = cookieValue.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, presented) && now.getEpochSecond() < expiry;
    }

    private String mac(long expiry) {
        byte[] passwordDigest = sha256(password);
        byte[] message = (expiry + ".").getBytes(StandardCharsets.US_ASCII);
        byte[] input = new byte[message.length + passwordDigest.length];
        System.arraycopy(message, 0, input, 0, message.length);
        System.arraycopy(passwordDigest, 0, input, message.length, passwordDigest.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(macKey, input));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }

    private static byte[] hmac(byte[] key, String message) {
        return hmac(key, message.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(message);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is required of every JVM", impossible);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
