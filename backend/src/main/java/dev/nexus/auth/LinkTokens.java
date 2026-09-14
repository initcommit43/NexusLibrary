package dev.nexus.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Minting and recognising the tokens that travel in a mailed link.
 *
 * <p>Shared by the reset and the verification flow so the two cannot drift: a link that is
 * 32 bytes in one place and 16 in the other, or digested one way here and another there, is a
 * difference nobody would notice until the weaker of the two was the one being guessed.
 */
final class LinkTokens {

    /**
     * 32 bytes. Far past guessing, and short enough that the URL survives a mail client that
     * decides where to wrap a line.
     */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkTokens() {}

    /** A fresh token, for the link itself. Never stored. */
    static String mint() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        // URL-safe and unpadded: the token travels as a query parameter, and '+' or '=' in one
        // is a link that works until something along the way decides to re-encode it.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * What goes in the table, and what a presented token is looked up by.
     *
     * <p>A plain digest rather than a password hash. bcrypt exists to make guessing a
     * human-chosen secret expensive; there is nothing to guess in 32 random bytes, and a
     * salted hash could not be looked up by anyway.
     */
    static String digestOf(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }
}
