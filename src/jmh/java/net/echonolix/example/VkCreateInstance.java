package net.echonolix.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

public class VkCreateInstance extends VkBenchmarkBase {
    @Benchmark
    public void caelum(Blackhole blackHole) {
        CaelumKt.caelumCreateVkInstance(blackHole);
    }

    @Benchmark
    public void lwjgl(Blackhole blackHole) {
        LWJGLKt.lwjglCreateVkInstance(blackHole);
    }
}
