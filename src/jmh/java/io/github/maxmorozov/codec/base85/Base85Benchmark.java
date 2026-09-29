/*
 * Copyright (C) 2026 Maksim Morozov
 *
 * This software is licensed under the MIT License.
 */
package io.github.maxmorozov.codec.base85;

import org.openjdk.jmh.annotations.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS) // measured in microseconds (lower is better)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class Base85Benchmark {

    @Param({"1000", "100000"}) // Testing with small and large arrays
    private int dataSize;

    private byte[] rawData;
    private byte[] encodedData;
    private byte[] buffer;

    @Setup
    public void setup() {
        rawData = new byte[dataSize];
        new Random(42).nextBytes(rawData);
        encodedData = new byte[Base85Codec.encodedLength(rawData.length)];
        int encodedSize = Base85Codec.encode(rawData, encodedData, 0);
        buffer = new byte[encodedSize];
    }

    // --- BENCHMARKS FOR ENCODE ---

    @Benchmark
    public int testEncode() {
        return Base85Codec.encode(rawData, 0, rawData.length, buffer, 0);
    }

    // --- BENCHMARKS FOR DECODE ---

    @Benchmark
    public int testDecode() {
        return Base85Codec.decode(encodedData, 0, encodedData.length, buffer, 0);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(Base85Benchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}
