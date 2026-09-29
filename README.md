# Base85 Codec for Java

A high-performance, zero-allocation Base-85 encoder and decoder implementation for Java using the RFC 1924 alphabet.

This codec is specifically optimized for performance-critical scenarios where data needs to be processed rapidly with minimal overhead. 

*Note: This implementation processes data as a single continuous block and is designed for maximum throughput without intermediate stream buffering.*

## Features

* High Performance: Uses division-by-multiplication techniques (MAGIC multipliers) to eliminate expensive CPU division operations.
* Zero Heap Allocation: Designed for zero GC pressure; encoding and decoding are performed directly into your pre-allocated byte buffers.
* Precise Diagnostics: Detailed error reporting that pinpoints the exact corrupted character and byte position when invalid data is encountered.
* Extensively Tested: Validated via strict sequential fuzzing, boundary padding checks, and overflow testing.

## Alphabet (RFC 1924 / Git Variant)

The codec utilizes the official RFC 1924 character set consisting of 85 printable ASCII characters in this exact order:
0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~

This specific alphabet is used for:
* Compact representation of IPv6 addresses (as defined in RFC 1924).
* Encoding raw binary payloads inside Git binary patches ('git diff --binary').

*Note: This library implements the pure data-to-character encoding logic. It does not parse or generate Git's internal block-length prefix characters.*

The absolute last character (~) serves dynamically as the padding/filler value.

## Usage

### Encoding Data
```java
byte[] data = "Hello, World!".getBytes(StandardCharsets.ISO_8859_1);
int expectedSize = Base85Codec.encodedLength(data.length);

byte[] target = new byte[expectedSize];
int encodedBytes = Base85Codec.encode(data, target, 0);

String encodedString = new String(target, 0, encodedBytes, StandardCharsets.ISO_8859_1);
System.out.println(encodedString);
```

### Decoding Data
```java
byte[] encodedData = "08WInE_`Y4E^g0b".getBytes(StandardCharsets.ISO_8859_1);
int expectedSize = Base85Codec.decodedLength(encodedData.length);

byte[] target = new byte[expectedSize];
int decodedBytes = Base85Codec.decode(encodedData, target, 0);
```

## Errors and Exception Handling

The codec strictly validates all input parameters before processing and throws appropriate exceptions to ensure fast and precise diagnostics:

### IndexOutOfBoundsException
* Invalid Source Start: Thrown if the `start` index is negative or exceeds the source array length.
* Source Range Out of Bounds: Thrown if the window defined by `start + length` exceeds the source array capacity (validated using overflow-safe subtraction).
* Invalid Target Offset: Thrown if the `targetOffset` is negative or exceeds the destination array length.

### IllegalArgumentException
* Negative Length: Thrown if the requested processing `length` is less than zero.
* Buffer Too Small: Thrown if the provided target byte array lacks sufficient capacity relative to the given offset and required output size.
* Invalid Character: Thrown if the input data contains characters outside the RFC 1924 alphabet. The error message explicitly states the illegal character and its hexadecimal code (e.g., "Invalid Base85 character: ' ' (0x20)").
* Invalid Input Length: Thrown if the encoded input length modulo 5 equals 1 (e.g., 1, 6, 11 chars), which is mathematically impossible to decode in Base85.
* Value Overflow: Thrown if a 5-character sequence decodes into a 32-bit unsigned value exceeding 2^32 - 1 (4,294,967,295). For example, the sequence "~~~~~" evaluates to 4,437,053,124, which triggers an overflow exception. This validation applies to both full blocks and padded tail paths.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

### Acknowledgments
Portions of this software are derived from the Eclipse JGit Base85 implementation (Copyright (C) 2021 Thomas Wolf and others), used under the terms of the Eclipse Distribution License v. 1.0 (BSD-3-Clause).
