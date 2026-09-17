package com.reelcipe.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates RFC 9562 UUID version 7 identifiers.
 *
 * <p>The random portion is incremented for identifiers created within the same
 * millisecond. This keeps identifiers strictly ordered and preserves database
 * index locality, including when the system clock briefly moves backwards.</p>
 */
public final class UuidV7 {

    private static final long RANDOM_B_MASK = 0x3fff_ffff_ffff_ffffL;
    private static final int RANDOM_A_MASK = 0x0fff;
    private static final long VERSION_7 = 0x7000L;
    private static final long IETF_VARIANT = 0x8000_0000_0000_0000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static long lastUnixMillis = -1;
    private static int randomA;
    private static long randomB;

    private UuidV7() {
    }

    public static synchronized UUID randomUuid() {
        long unixMillis = System.currentTimeMillis();
        if (unixMillis > lastUnixMillis) {
            lastUnixMillis = unixMillis;
            randomA = RANDOM.nextInt(RANDOM_A_MASK + 1);
            randomB = RANDOM.nextLong() & RANDOM_B_MASK;
        } else {
            incrementRandomPayload();
        }

        long mostSignificantBits = (lastUnixMillis << 16)
                | VERSION_7
                | randomA;
        long leastSignificantBits = IETF_VARIANT | randomB;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static void incrementRandomPayload() {
        if (randomB < RANDOM_B_MASK) {
            randomB++;
            return;
        }

        randomB = 0;
        if (randomA < RANDOM_A_MASK) {
            randomA++;
            return;
        }

        // Exhausting 74 bits in one millisecond is practically impossible,
        // but advancing the timestamp preserves uniqueness and ordering.
        lastUnixMillis++;
        randomA = RANDOM.nextInt(RANDOM_A_MASK + 1);
        randomB = RANDOM.nextLong() & RANDOM_B_MASK;
    }
}
