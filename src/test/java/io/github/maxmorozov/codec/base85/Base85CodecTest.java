/*
 * Copyright (C) 2026 Maksim Morozov
 *
 * This software is licensed under the MIT License.
 *
 * Portions of this software are based on Eclipse JGit's Base85 implementation.
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * These portions are subject to the terms and conditions of the Eclipse Distribution
 * License v. 1.0, available at https://www.eclipse.org/org/documents/edl-v10.php
 * SPDX-License-Identifier: BSD-3-Clause
 */
package io.github.maxmorozov.codec.base85;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Base85Codec}.
 */
public class Base85CodecTest {
    private static final String VALID_CHARS = "0123456789"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            + "!#$%&()*+-;<=>?@^_`{|}~";

    @Test
    public void testChars() {
        byte[] decoded = new byte[4];
        for (int i = 0; i < 256; i++) {
            byte[] testData = {'1', '2', '3', '4', (byte) i};
            if (VALID_CHARS.indexOf(i) >= 0) {
                int decodedBytes = Base85Codec.decode(testData, decoded, 0);
                assertEquals(4, decodedBytes);
            } else {
                assertThrows(IllegalArgumentException.class,
                        () -> Base85Codec.decode(testData, decoded, 0));
            }
        }
    }

    private void roundtrip(byte[] data, int expectedLength) {
        byte[] encoded = new byte[expectedLength];
        int encodedBytes = Base85Codec.encode(data, encoded, 0);
        assertEquals(expectedLength, encodedBytes);

        byte[] buffer = new byte[data.length];
        int decodedBytes = Base85Codec.decode(encoded, buffer, 0);
        assertEquals(decodedBytes, data.length);
        assertArrayEquals(data, buffer);
    }

    private void roundtrip(String data, int expectedLength) {
        roundtrip(data.getBytes(StandardCharsets.ISO_8859_1), expectedLength);
    }

    @Test
    public void testPadding() {
        roundtrip("", 0);
        roundtrip("a", 2);
        roundtrip("ab", 3);
        roundtrip("abc", 4);
        roundtrip("abcd", 5);
        roundtrip("abcde", 7);
        roundtrip("abcdef", 8);
        roundtrip("abcdefg", 9);
        roundtrip("abcdefgh", 10);
        roundtrip("abcdefghi", 12);
    }

    @Test
    public void testBinary() {
        roundtrip(new byte[]{1}, 2);
        roundtrip(new byte[]{1, 2}, 3);
        roundtrip(new byte[]{1, 2, 3}, 4);
        roundtrip(new byte[]{1, 2, 3, 4}, 5);
        roundtrip(new byte[]{1, 2, 3, 4, 5}, 7);
        roundtrip(new byte[]{1, 2, 3, 4, 5, 0, 0, 0}, 10);
        roundtrip(new byte[]{1, 2, 3, 4, 0, 0, 0, 5}, 10);
    }

    @Test
    public void testOverflow() {
        byte[] buffer = new byte[10];
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'~', '~', '~', '~', '~'}, buffer, 0));
        assertTrue(e.getMessage().contains("overflow"));
    }

    @Test
    public void testInvalidLengthRemainderOne() {
        byte[] buffer = new byte[10];

        // Tail length 1 is invalid
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'0'}, buffer, 0));
        assertTrue(e1.getMessage().contains("invalid length"));

        // Any full groups + tail length 1 is also invalid (6 = 5 + 1)
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'0', '0', '0', '0', '0', '0'}, buffer, 0));
        assertTrue(e2.getMessage().contains("invalid length"));
    }

    @Test
    public void testValidTailLengths() {
        byte[] buffer = new byte[10];
        // 2/3/4-char tails are valid with tail decoding support
        assertEquals(1, Base85Codec.decode(new byte[]{'0', '0'}, buffer, 0));
        assertEquals(2, Base85Codec.decode(new byte[]{'0', '0', '0'}, buffer, 0));
        assertEquals(3, Base85Codec.decode(new byte[]{'0', '0', '0', '0'}, buffer, 0));
    }

    @Test
    public void testZeroLengthIsValid() {
        byte[] buffer = new byte[10];
        assertEquals(0, Base85Codec.decode(new byte[0], buffer, 0));
    }

    @Test
    public void testPreciseErrorDiagnostic() {
        byte[] buffer = new byte[10];

        // Test illegal character in the middle of a full block (Fast Path)
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'0', '0', ' ', '0', '0'}, buffer, 0));
        assertTrue(e1.getMessage().contains("Invalid Base85 character: ' ' (0x20)"));

        // Test illegal character in the tail path
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'0', '0', '0', '\n'}, buffer, 0));
        assertTrue(e2.getMessage().contains("Invalid Base85 character: '?' (0x0A)"));
    }

    @Test
    public void testFuzzingSequentialZeroAllocation() {
        Random rnd = new Random(42);

        // Allocate maximum required buffers ONCE before entering the loop
        int maxRawLength = 8192;
        int maxEncodedLength = Base85Codec.encodedLength(maxRawLength); // 10240 bytes

        byte[] srcDataPool = new byte[maxRawLength];
        byte[] encodeBufferPool = new byte[maxEncodedLength];
        byte[] decodeBufferPool = new byte[maxRawLength];

        // Fill the entire source pool with random bytes once
        rnd.nextBytes(srcDataPool);

        // Strictly iterate through every single length from 0 to maxRawLength
        // 0 heap allocations inside this loop!
        for (int length = 0; length <= maxRawLength; length++) {

            // Calculate exactly how many bytes are expected for the current window length
            int expectedEncLen = Base85Codec.encodedLength(length);

            // Encode using the shared pools
            int actualEncLen = Base85Codec.encode(srcDataPool, 0, length, encodeBufferPool, 0);
            assertEquals(expectedEncLen, actualEncLen);

            // Decode back into the shared decode pool
            int actualDecLen = Base85Codec.decode(encodeBufferPool, 0, actualEncLen, decodeBufferPool, 0);
            assertEquals(length, actualDecLen);

            // Verify slice equivalence directly from the pre-allocated buffers
            for (int j = 0; j < length; j++) {
                if (srcDataPool[j] != decodeBufferPool[j]) {
                    fail("Fuzzing mismatch detected at index " + j + " for sequential length " + length);
                }
            }
        }
    }

    @Test
    public void testNegativeLengthThrows() {
        byte[] buffer = new byte[10];

        // Test encode with negative length
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.encode(new byte[]{1, 2, 3}, 0, -1, buffer, 0));
        assertEquals("Length must be non-negative", e1.getMessage());

        // Test decode with negative length
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'0', '0'}, 0, -1, buffer, 0));
        assertEquals("Length must be non-negative", e2.getMessage());
    }

    @Test
    public void testTargetBufferTooSmallThrows() {
        byte[] data = new byte[]{1, 2, 3, 4}; // Requires exactly 5 bytes encoded
        byte[] encoded = new byte[]{'0', '0', '0', '0', '0'}; // Requires exactly 4 bytes decoded

        // 1. Test encode buffer check
        byte[] smallEncodeBuffer = new byte[4]; // 4 < 5
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.encode(data, 0, data.length, smallEncodeBuffer, 0));
        assertTrue(e1.getMessage().contains("Target buffer is too small for encoded data"));

        // 2. Test decode buffer check
        byte[] smallDecodeBuffer = new byte[3]; // 3 < 4
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(encoded, 0, encoded.length, smallDecodeBuffer, 0));
        assertEquals("Target buffer is too small for decoded data. Expected size: 4. Actual size: 3", e2.getMessage());
    }

    @Test
    public void testTailPathOverflowThrows() {
        byte[] buffer = new byte[10];

        // 1. Test 2-char tail path overflow (leftover == 2)
        // Two maximum characters '~~'. The missing 3 trailing characters are padded with FILLER_VAL (84) inside the codec.
        // Formula: 84 * 85^4 + 84 * 85^3 + 84 * 85^2 + 84 * 85 + 84 = 4,437,053,124 > UINT_MAX
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'~', '~'}, buffer, 0));
        assertEquals("Base-85 value overflow in padding", e1.getMessage());

        // 2. Test 3-char tail path overflow (leftover == 3)
        // Three maximum characters '~~~'. Padded with two FILLER_VAL (84).
        // Sum is exactly the same: 4,437,053,124 > UINT_MAX
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'~', '~', '~'}, buffer, 0));
        assertEquals("Base-85 value overflow in padding", e2.getMessage());

        // 3. Test 4-char tail path overflow (leftover == 4)
        // Four maximum characters '~~~~'. Padded with one FILLER_VAL (84).
        // Sum is exactly the same: 4,437,053,124 > UINT_MAX
        IllegalArgumentException e3 = assertThrows(
                IllegalArgumentException.class,
                () -> Base85Codec.decode(new byte[]{'~', '~', '~', '~'}, buffer, 0));
        assertEquals("Base-85 value overflow in padding", e3.getMessage());
    }

    @Test
    public void testPreciseBoundsAndIndexValidation() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        byte[] buffer = new byte[10];

        // --- ENCODE method validation tests ---

        // 1. Negative length (IllegalArgumentException)
        assertThrows(IllegalArgumentException.class,
                () -> Base85Codec.encode(data, 0, -1, buffer, 0),
                "Negative length must throw IllegalArgumentException");

        // 2. Negative start index (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.encode(data, -1, 2, buffer, 0),
                "Negative start index must throw IndexOutOfBoundsException");

        // 3. Start index exceeds array length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.encode(data, data.length + 1, 1, buffer, 0),
                "Start index exceeding array bounds must throw IndexOutOfBoundsException");

        // 4. Source window (start + length) exceeds array length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.encode(data, 3, 3, buffer, 0), // 3 + 3 = 6 > 5
                "Source range exceeding array bounds must throw IndexOutOfBoundsException");

        // 5. Negative target offset (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.encode(data, 0, 4, buffer, -1),
                "Negative target offset must throw IndexOutOfBoundsException");

        // 6. Target offset exceeds destination buffer length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.encode(data, 0, 4, buffer, buffer.length + 1),
                "Target offset exceeding buffer bounds must throw IndexOutOfBoundsException");


        // --- DECODE method validation tests ---
        byte[] encodedData = new byte[]{'0', '0', '0', '0', '0'}; // 5 characters

        // 1. Negative length (IllegalArgumentException)
        assertThrows(IllegalArgumentException.class,
                () -> Base85Codec.decode(encodedData, 0, -1, buffer, 0),
                "Negative length must throw IllegalArgumentException");

        // 2. Negative start index (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.decode(encodedData, -1, 5, buffer, 0),
                "Negative start index must throw IndexOutOfBoundsException");

        // 3. Start index exceeds encoded array length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.decode(encodedData, encodedData.length + 1, 1, buffer, 0),
                "Start index exceeding encoded bounds must throw IndexOutOfBoundsException");

        // 4. Encoded window (start + length) exceeds array length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.decode(encodedData, 2, 4, buffer, 0), // 2 + 4 = 6 > 5
                "Encoded range exceeding array bounds must throw IndexOutOfBoundsException");

        // 5. Negative target offset (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.decode(encodedData, 0, 5, buffer, -1),
                "Negative target offset must throw IndexOutOfBoundsException");

        // 6. Target offset exceeds destination buffer length (IndexOutOfBoundsException)
        assertThrows(IndexOutOfBoundsException.class,
                () -> Base85Codec.decode(encodedData, 0, 5, buffer, buffer.length + 1),
                "Target offset exceeding buffer bounds must throw IndexOutOfBoundsException");
    }

}
