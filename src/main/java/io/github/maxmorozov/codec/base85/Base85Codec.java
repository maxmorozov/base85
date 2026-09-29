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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Base-85 encoder/decoder.
 */
public final class Base85Codec {

    // Magic multiplier for unsigned division by 85
    // For any unsigned int n in range [0..4294967295]
    // n / 85 = (n * 3233857729L) >>> 38
    static final long MAGIC = 3233857729L; // ceil((2^38)/85)

    // Magic multipliers for unsigned division by 85^2 and 85^3 in a single step,
    // used to collapse repeated MAGIC divisions in the encode tail path.
    // Verified by exhaustive check over all residue classes mod 85^2 / 85^3
    // for every unsigned int n in range [0..4294967295], plus randomized
    // cross-checks against the chained single-step MAGIC divisions.
    // n / 85^2 = (n * 2434904643L) >>> 44
    static final long MAGIC2 = 2434904643L; // ceil((2^44)/85^2)
    // n / 85^3 = (n * 3666679933L) >>> 51
    static final long MAGIC3 = 3666679933L; // ceil((2^51)/85^3)

    private static final long POWER_4 = 52200625L; // 85^4
    private static final long POWER_3 = 614125L;   // 85^3
    private static final int POWER_2 = 7225;       // 85^2


    private static final byte[] ENCODE = ("0123456789"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "abcdefghijklmnopqrstuvwxyz"
            + "!#$%&()*+-;<=>?@^_`{|}~")
            .getBytes(StandardCharsets.ISO_8859_1);

    private static final int[] DECODE = new int[256];
    public static final int FILLER_VAL;

    static {
        Arrays.fill(DECODE, -1);
        for (int i = 0; i < ENCODE.length; i++) {
            DECODE[ENCODE[i]] = i;
        }
        // Enforce the padding character as the absolute last character of our alphabet ('~')
        FILLER_VAL = ENCODE.length - 1;
    }

    private Base85Codec() {
        // No instantiation
    }

    /**
     * Determines the length of the base-85 encoding for {@code rawLength}
     * bytes.
     *
     * @param rawLength number of bytes to encode
     * @return number of bytes needed for the base-85 encoding of
     * {@code rawLength} bytes
     */
    public static int encodedLength(int rawLength) {
        // The last non-zero n = (rawLength % 4) bytes should be padded with zeros up to 4 bytes.
        // After encoding we discard the last (4 - n) bytes, so
        // the tail will be 5 - (4 - n) = n + 1 encoded bytes.

        // Encoded length is calculated as:
        // len = (rawLength / 4) * 5 + (rawLength % 4) + (rawLength % 4 == 0 ? 0 : 1)
        // Below is optimized version of the expression above

        // We need to use intermediate values of type `long` to avoid overflow.
        return (int) ((rawLength * 5L + 3) >> 2);
    }

    /**
     * Determines the length of the base-85 decoding for {@code length} bytes.
     *
     * @param length number of bytes to decode
     * @return number of bytes needed for base-85 decoding of {@code length} bytes
     */
    public static int decodedLength(int length) {
        // int div = length / 5;
        // int rem = length % 5;

        // Decoded block size is calculated as:
        // len = div * 4 + (rem == 0 ? 0 : rem - 1)

        // optimized version
        return (int) ((length * 4L) / 5);

    }

    /**
     * Encodes the given {@code data} in Base-85.
     *
     * @param data         to encode
     * @param target       buffer for the result
     * @param targetOffset start offset of the result in the target buffer
     * @return encoded data length
     */
    public static int encode(byte[] data, byte[] target, int targetOffset) {
        return encode(data, 0, data.length, target, targetOffset);
    }

    /**
     * Encodes {@code length} bytes of {@code data} in Base-85, beginning at the
     * {@code start} index.
     *
     * @param data         to encode
     * @param start        index of the first byte to encode
     * @param length       number of bytes to encode
     * @param target       buffer for the result
     * @param targetOffset start offset of the result in the target buffer
     * @return encoded data length
     */
    public static int encode(byte[] data, int start, int length, byte[] target, int targetOffset) {
        if (length < 0) {
            throw new IllegalArgumentException("Length must be non-negative");
        }
        if (start < 0 || start > data.length) {
            throw new IndexOutOfBoundsException("Invalid source start index: " + start + " (data.length=" + data.length + ")");
        }
        if (targetOffset < 0 || targetOffset > target.length) {
            throw new IndexOutOfBoundsException("Invalid target offset: " + targetOffset);
        }

        int expectedSize = encodedLength(length);

        int targetSize = target.length - targetOffset;
        if (targetSize < expectedSize) {
            throw new IllegalArgumentException(
                    "Target buffer is too small for encoded data. Expected size: " + expectedSize +
                            ". Actual size: " + targetSize);
        }

        int in = start;
        int out = targetOffset;

        // Fast path: process full 4-byte blocks
        int fastEnd = start + (length & ~3);
        while (in < fastEnd) {
            long acc = ((data[in++] & 0xFFL) << 24) |
                    ((data[in++] & 0xFFL) << 16) |
                    ((data[in++] & 0xFFL) << 8) |
                    (data[in++] & 0xFFL);

            long q = (acc * MAGIC) >>> 38;                  // q = acc / 85
            target[out + 4] = ENCODE[(int) (acc - q * 85)]; // r = acc % 85
            acc = q;
            q = (acc * MAGIC) >>> 38;
            target[out + 3] = ENCODE[(int) (acc - q * 85)];
            acc = q;
            q = (acc * MAGIC) >>> 38;
            target[out + 2] = ENCODE[(int) (acc - q * 85)];
            acc = q;
            q = (acc * MAGIC) >>> 38;
            target[out + 1] = ENCODE[(int) (acc - q * 85)];
            target[out] = ENCODE[(int) q];

            out += 5;
        }

        // Tail path: process remaining 1 to 3 bytes.
        // Unrolled per exact tail length (no loop, no per-iteration branch),
        // using the same MAGIC multiply-shift trick as the fast path.
        // Digits that would only be discarded (padding positions) are advanced
        // with a single collapsed division (MAGIC2/MAGIC3) instead of chaining
        // multiple MAGIC divisions, since their remainder is never needed.
        int end = start + length;
        if (in < end) {
            long acc = ((long) (data[in++] & 0xFF)) << 24;
            if (in < end) {
                acc |= (long) (data[in++] & 0xFF) << 16;
                if (in < end) {
                    acc |= (long) (data[in] & 0xFF) << 8;

                    // 3 tail bytes -> 4 output symbols, discard 1 low digit
                    acc = (acc * MAGIC) >>> 38;
                    long q = (acc * MAGIC) >>> 38;
                    target[out + 3] = ENCODE[(int) (acc - q * 85)];
                    acc = q;
                    q = (acc * MAGIC) >>> 38;
                    target[out + 2] = ENCODE[(int) (acc - q * 85)];
                    acc = q;
                    q = (acc * MAGIC) >>> 38;
                    target[out + 1] = ENCODE[(int) (acc - q * 85)];
                    target[out] = ENCODE[(int) q];
                    out += 4;
                } else {
                    // 2 tail bytes -> 3 output symbols, discard 2 low digits in one step
                    acc = (acc * MAGIC2) >>> 44;
                    long q = (acc * MAGIC) >>> 38;
                    target[out + 2] = ENCODE[(int) (acc - q * 85)];
                    acc = q;
                    q = (acc * MAGIC) >>> 38;
                    target[out + 1] = ENCODE[(int) (acc - q * 85)];
                    target[out] = ENCODE[(int) q];
                    out += 3;
                }
            } else {
                // 1 tail byte -> 2 output symbols, discard 3 low digits in one step
                acc = (acc * MAGIC3) >>> 51;
                long q = (acc * MAGIC) >>> 38;
                target[out + 1] = ENCODE[(int) (acc - q * 85)];
                target[out] = ENCODE[(int) q];
                out += 2;
            }
        }
        return out - targetOffset;
    }

    /**
     * Decodes the Base-85 {@code encoded} data into a byte array.
     *
     * @param encoded      Base-85 encoded data
     * @param target       buffer for the result
     * @param targetOffset start offset of the result in the target buffer
     * @return the decoded bytes length
     * @throws IllegalArgumentException if the remainder of {@code length} divided by 5 is 1,
     *                                  or there are invalid characters in the encoded data
     */
    public static int decode(byte[] encoded, byte[] target, int targetOffset) {
        return decode(encoded, 0, encoded.length, target, targetOffset);
    }

    /**
     * Decodes {@code length} bytes of Base-85 {@code encoded} data, beginning
     * at the {@code start} index, into a byte array.
     *
     * @param encoded      Base-85 encoded data
     * @param start        index at which the data to decode starts in {@code encoded}
     * @param length       of the Base-85 encoded data
     * @param target       buffer for the result
     * @param targetOffset start offset of the result in the target buffer
     * @return the decoded bytes length
     * @throws IllegalArgumentException if the remainder of {@code length} divided by 5 is 1,
     *                                  or there are invalid characters in the encoded data
     */
    public static int decode(byte[] encoded, int start, int length, byte[] target, int targetOffset) {
        if (length < 0) {
            throw new IllegalArgumentException("Length must be non-negative");
        }
        if (start < 0 || start > encoded.length) {
            throw new IndexOutOfBoundsException("Invalid source start index: " + start + " (encoded.length=" + encoded.length + ")");
        }
        if (targetOffset < 0 || targetOffset > target.length) {
            throw new IndexOutOfBoundsException("Invalid target offset: " + targetOffset);
        }

        int expectedSize = decodedLength(length);

        // Guard clause: Ensure target buffer is big enough before entering any loops
        int targetSize = target.length - targetOffset;
        if (targetSize < expectedSize) {
            throw new IllegalArgumentException(
                    "Target buffer is too small for decoded data. Expected size: " + expectedSize +
                            ". Actual size: " + targetSize);
        }

        int in = start;
        int out = targetOffset;
        int fastEnd = start + (length / 5) * 5;

        // Fast path: process full 5-character blocks
        while (in < fastEnd) {
            int v0 = DECODE[encoded[in++] & 0xFF];
            int v1 = DECODE[encoded[in++] & 0xFF];
            int v2 = DECODE[encoded[in++] & 0xFF];
            int v3 = DECODE[encoded[in++] & 0xFF];
            int v4 = DECODE[encoded[in++] & 0xFF];

            if ((v0 | v1 | v2 | v3 | v4) < 0) {
                // Pinpoint error immediately by scanning 5 read characters
                analyzeAndThrowInvalidChar(encoded, in, 5);
            }

            // Parallel ILP multiplication tree (64-bit safe bounds check against UINT_MAX)
            long p01 = v0 * POWER_4 + v1 * POWER_3;
            int p23 = v2 * POWER_2 + v3 * 85;
            long acc = p01 + p23 + v4;

            if (acc > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException("Base-85 value overflow");
            }

            target[out++] = (byte) (acc >>> 24);
            target[out++] = (byte) (acc >>> 16);
            target[out++] = (byte) (acc >>> 8);
            target[out++] = (byte) acc;
        }

        // Tail path: straight-line logic; missing characters are padded with the last alphabet symbol
        int leftover = (start + length) - fastEnd;
        if (leftover > 0) {
            if (leftover == 1) {
                throw new IllegalArgumentException(
                        "Base-85 encoded data has invalid length: remainder 1 when divided by 5");
            }

            int v0, v1, v2 = 0, v3 = 0, v4;

            v0 = DECODE[encoded[in++] & 0xFF];
            v1 = DECODE[encoded[in++] & 0xFF];
            if (leftover >= 3) {
                v2 = DECODE[encoded[in++] & 0xFF];
            }
            if (leftover == 4) {
                v3 = DECODE[encoded[in++] & 0xFF];
            }

            if ((v0 | v1 | v2 | v3) < 0) {
                // Pinpoint error by scanning exactly the number of characters read in the tail
                analyzeAndThrowInvalidChar(encoded, in, leftover);
            }

            // Pad missing positions with the highest digit (84, '~')
            if (leftover == 2) {
                v2 = FILLER_VAL;
                v3 = FILLER_VAL;
                v4 = FILLER_VAL;
            } else if (leftover == 3) {
                v3 = FILLER_VAL;
                v4 = FILLER_VAL;
            } else {
                v4 = FILLER_VAL;
            }

            long p01 = v0 * POWER_4 + v1 * POWER_3;
            int p23 = v2 * POWER_2 + v3 * 85;
            long acc = p01 + p23 + v4;

            if (acc > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException("Base-85 value overflow in padding");
            }

            target[out++] = (byte) (acc >>> 24);
            if (leftover >= 3) {
                target[out++] = (byte) (acc >>> 16);
            }
            if (leftover == 4) {
                target[out++] = (byte) (acc >>> 8);
            }
        }
        return out - targetOffset;
    }

    private static void analyzeAndThrowInvalidChar(byte[] src, int in, int charsToRollback) {
        // Position the index precisely at the start of the corrupted block
        int idx = in - charsToRollback;

        // Scan sequentially until the first bad character is found
        while (idx < in) {
            byte ch = src[idx++];
            if (DECODE[ch & 0xFF] < 0) {
                char printable = (ch >= 32 && ch < 127) ? (char) ch : '?';
                throw new IllegalArgumentException(String.format(
                        "Invalid Base85 character: '%c' (0x%02X)", printable, ch & 0xFF
                ));
            }
        }
    }
}
