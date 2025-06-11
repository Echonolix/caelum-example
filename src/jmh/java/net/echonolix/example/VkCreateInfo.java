package net.echonolix.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class VkCreateInfo extends VkBenchmarkBase {
    @Benchmark
    public void caelum(Blackhole blackHole) {
        CaelumCreateInfo.INSTANCE.run(blackHole);
    }

    @Benchmark
    public void lwjgl(Blackhole blackHole) {
        LWJGLCreateInfo.INSTANCE.run(blackHole);
    }
}
