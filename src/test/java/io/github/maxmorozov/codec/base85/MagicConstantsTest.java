/*
 * Copyright (C) 2026 Maksim Morozov
 *
 * This software is licensed under the MIT License.
 */
package io.github.maxmorozov.codec.base85;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exhaustively verifies the multiply-shift division constants over all uint32 values.
 */
public class MagicConstantsTest {
    @Test
    public void magicConstantsMatchDivisionForAllUnsignedInts() {
        for (long n = 0; n <= 0xFFFF_FFFFL; n++) {
            if (((n * Base85Codec.MAGIC) >>> 38) != n / 85) {
                fail("MAGIC mismatch for n=" + n);
            }
            if (((n * Base85Codec.MAGIC2) >>> 44) != n / 7225) {
                fail("MAGIC2 mismatch for n=" + n);
            }
            if (((n * Base85Codec.MAGIC3) >>> 51) != n / 614125) {
                fail("MAGIC3 mismatch for n=" + n);
            }
        }
    }
}
