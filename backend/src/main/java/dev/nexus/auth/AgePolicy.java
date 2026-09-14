package dev.nexus.auth;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

/**
 * The ages NexusLibrary draws a line at, written down once.
 *
 * <p>{@link #MINIMUM} is who may hold an account: sixteen, where DSGVO Art. 8 lets a reader in
 * Germany agree to terms without a guardian. {@link #ADULT} is who may switch adult material on,
 * the age § 5 JMStV is about.
 */
public final class AgePolicy {

    public static final int MINIMUM = 16;

    public static final int ADULT = 18;

    /** Nobody registering is older than this; an earlier date is a typo, not a reader. */
    public static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private AgePolicy() {}

    /**
     * Today in UTC. Every deployment has to agree on which day it is, or a container running in
     * one zone lets someone register an hour before one running in another.
     */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public static boolean isPlausible(LocalDate dateOfBirth) {
        return !dateOfBirth.isBefore(EARLIEST_BIRTH_DATE);
    }

    /** Whether someone born on {@code dateOfBirth} has turned {@code years} by {@code on}. */
    public static boolean hasTurned(LocalDate dateOfBirth, int years, LocalDate on) {
        return dateOfBirth != null && Period.between(dateOfBirth, on).getYears() >= years;
    }
}
