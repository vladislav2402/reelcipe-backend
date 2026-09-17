package com.reelcipe.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidV7Test {

    @Test
    void generatesVersionSevenUuidWithIetfVariantAndCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.randomUuid();
        long after = System.currentTimeMillis();

        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
        long embeddedTimestamp = uuid.getMostSignificantBits() >>> 16;
        assertTrue(embeddedTimestamp >= before);
        assertTrue(embeddedTimestamp <= after);
    }

    @Test
    void generatesUniqueStrictlyOrderedIdentifiers() {
        Set<UUID> generated = new HashSet<>();
        UUID previous = UuidV7.randomUuid();
        generated.add(previous);

        for (int index = 0; index < 10_000; index++) {
            UUID next = UuidV7.randomUuid();
            assertTrue(compareUnsigned(previous, next) < 0);
            assertTrue(generated.add(next));
            previous = next;
        }
    }

    private int compareUnsigned(UUID left, UUID right) {
        int mostSignificant = Long.compareUnsigned(
                left.getMostSignificantBits(),
                right.getMostSignificantBits());
        return mostSignificant != 0
                ? mostSignificant
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
