package net.echonolix.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Path;

public class VkCreateDevice extends VkBenchmarkBase {
    static {
        System.load(Path.of("run/glfw3.dll").toAbsolutePath().toString());
    }

    @Benchmark
    public void caelum(Blackhole blackHole) {
        CaelumKt.caelumCreateVkDevice(blackHole);
    }

    @Benchmark
    public void lwjgl(Blackhole blackHole) {
        LWJGLKt.lwjglCreateVkDevice(blackHole);
    }
}
